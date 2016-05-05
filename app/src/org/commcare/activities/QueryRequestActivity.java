package org.commcare.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.interfaces.HttpResponseProcessor;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.network.ModernHttpRequester;
import org.commcare.session.RemoteQuerySessionManager;
import org.commcare.suite.model.DisplayData;
import org.commcare.suite.model.DisplayUnit;
import org.commcare.suite.model.RemoteQueryDatum;
import org.commcare.suite.model.SessionDatum;
import org.commcare.tasks.SimpleHttpTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.media.MediaLayout;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.xml.ElementParser;
import org.javarosa.xml.TreeElementParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Map;

/**
 * Collects 'query datum' in the current session. Prompts user for query
 * params, makes query to server and stores xml 'fixture' response into current
 * session. Allows for 'case search and claim' workflow when used inside a
 * 'sync-request' entry in conjuction with entity select datum and sync
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@ManagedUi(R.layout.http_request_layout)
public class QueryRequestActivity
        extends SaveSessionCommCareActivity<QueryRequestActivity>
        implements HttpResponseProcessor {
    private static final String TAG = QueryRequestActivity.class.getSimpleName();

    private static final String ANSWERED_USER_PROMPTS_KEY = "answered_user_prompts";
    private static final String IN_ERROR_STATE_KEY = "in-error-state-key";
    private static final String ERROR_MESSAGE_KEY = "error-message-key";

    @UiElement(value = R.id.request_button, locale = "query.button")
    private Button queryButton;

    @UiElement(value = R.id.error_message)
    private TextView errorTextView;

    private boolean inErrorState;
    private String errorMessage;
    private RemoteQuerySessionManager remoteQuerySessionManager;
    private Hashtable<String, EditText> promptsBoxes = new Hashtable<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        remoteQuerySessionManager =
                buildQuerySessionManager(CommCareApplication._().getCurrentSessionWrapper());

        if (remoteQuerySessionManager == null) {
            Log.e(TAG, "Tried to launch remote query activity at wrong time in session.");
            setResult(RESULT_CANCELED);
            finish();
        } else {
            loadStateFromSavedInstance(savedInstanceState);

            setupUI();
        }
    }

    private void setupUI() {
        buildPromptUI();

        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                answerPrompts();
                makeQueryRequest();
            }
        });

        if (inErrorState) {
            enterErrorState();
        }
    }

    private void buildPromptUI() {
        LinearLayout promptsLayout = (LinearLayout)findViewById(R.id.query_prompts);
        Hashtable<String, DisplayUnit> userInputDisplays =
                remoteQuerySessionManager.getNeededUserInputDisplays();
        for (Map.Entry<String, DisplayUnit> displayEntry : userInputDisplays.entrySet()) {
            buildPromptEntry(promptsLayout, displayEntry.getKey(), displayEntry.getValue());
        }
    }

    private void buildPromptEntry(LinearLayout promptsLayout,
                                  String promptId,
                                  DisplayUnit displayUnit) {
        Hashtable<String, String> userAnswers =
                remoteQuerySessionManager.getUserAnswers();
        promptsLayout.addView(createPromptMedia(displayUnit));

        EditText promptEditText = new EditText(this);
        if (userAnswers.containsKey(promptId)) {
            promptEditText.setText(userAnswers.get(promptId));
        }
        promptEditText.setBackgroundResource(R.drawable.login_edit_text);
        promptsLayout.addView(promptEditText);
        promptsBoxes.put(promptId, promptEditText);
    }

    private void answerPrompts() {
        for (Map.Entry<String, EditText> promptEntry : promptsBoxes.entrySet()) {
            String promptText = promptEntry.getValue().getText().toString();
            if (!"".equals(promptText)) {
                remoteQuerySessionManager.answerUserPrompt(promptEntry.getKey(), promptText);
            }
        }
    }

    private void makeQueryRequest() {
        clearErrorState();
        URL url = null;
        String urlString = remoteQuerySessionManager.getBaseUrl();
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            enterErrorState(Localization.get("post.malformed.url", urlString));
        }

        if (url != null) {
            SimpleHttpTask httpTask;
            try {
                httpTask = new SimpleHttpTask(this, url, remoteQuerySessionManager.getRawQueryParams(), false);
            } catch (ModernHttpRequester.PlainTextPasswordException e) {
                enterErrorState(Localization.get("post.not.using.https", url.toString()));
                return;
            }
            httpTask.connect((CommCareTaskConnector)this);
            httpTask.executeParallel();
        }
    }

    private void clearErrorState() {
        errorMessage = "";
        inErrorState = false;
    }

    private void enterErrorState(String message) {
        errorMessage = message;
        enterErrorState();
    }

    private void enterErrorState() {
        inErrorState = true;
        Log.e(TAG, errorMessage);
        errorTextView.setText(errorMessage);
        errorTextView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        answerPrompts();

        savedInstanceState.putSerializable(ANSWERED_USER_PROMPTS_KEY,
                remoteQuerySessionManager.getUserAnswers());
        savedInstanceState.putString(ERROR_MESSAGE_KEY, errorMessage);
        savedInstanceState.putBoolean(IN_ERROR_STATE_KEY, inErrorState);
    }

    private static RemoteQuerySessionManager buildQuerySessionManager(AndroidSessionWrapper sessionWrapper) {
        SessionDatum datum;
        try {
            datum = sessionWrapper.getSession().getNeededDatum();
        } catch (NullPointerException e) {
            // tried loading session info when it wasn't there
            return null;
        }
        if (datum instanceof RemoteQueryDatum) {
            return new RemoteQuerySessionManager((RemoteQueryDatum)datum, sessionWrapper.getEvaluationContext());
        } else {
            return null;
        }
    }

    private void loadStateFromSavedInstance(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            errorMessage = savedInstanceState.getString(ERROR_MESSAGE_KEY);
            inErrorState = savedInstanceState.getBoolean(IN_ERROR_STATE_KEY);
            Hashtable<String, String> answeredPrompts =
                    (Hashtable<String, String>)savedInstanceState.getSerializable(ANSWERED_USER_PROMPTS_KEY);
            if (answeredPrompts != null) {
                for (Map.Entry<String, String> entry : answeredPrompts.entrySet()) {
                    remoteQuerySessionManager.answerUserPrompt(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private MediaLayout createPromptMedia(DisplayUnit display) {
        DisplayData displayData = display.evaluate();
        String promptText =
                Localizer.processArguments(displayData.getName(), new String[]{""}).trim();
        TextView text = new TextView(getApplicationContext());
        text.setText(promptText);

        int padding = (int)getResources().getDimension(R.dimen.help_text_padding);
        text.setPadding(0, 0, 0, 7);

        MediaLayout helpLayout = new MediaLayout(this);
        helpLayout.setAVT(text, displayData.getAudioURI(), displayData.getImageURI(), true);
        helpLayout.setPadding(padding, padding, padding, padding);
        text.setTextColor(Color.BLACK);

        return helpLayout;
    }

    @Override
    public void processSuccess(int responseCode, InputStream responseData) {
        Pair<ExternalDataInstance, String> instanceOrError =
                buildExternalDataInstance(responseData,
                        remoteQuerySessionManager.getStorageInstanceName());
        if (instanceOrError.first == null) {
            enterErrorState(Localization.get("query.response.format.error", instanceOrError.second));
        } else {
            CommCareApplication._().getCurrentSession().setQueryDatum(instanceOrError.first);
            setResult(RESULT_OK);
            finish();
        }
    }

    public static Pair<ExternalDataInstance, String> buildExternalDataInstance(InputStream instanceStream,
                                                                               String instanceId) {
        TreeElement root;
        try {
            KXmlParser baseParser = ElementParser.instantiateParser(instanceStream);
            root = new TreeElementParser(baseParser, 0, instanceId).parse();
        } catch (InvalidStructureException | IOException
                | XmlPullParserException | UnfullfilledRequirementsException e) {
            return new Pair<>(null, e.getMessage());
        }
        return new Pair<>(ExternalDataInstance.buildFromRemote(instanceId, root), "");
    }

    @Override
    public void processRedirection(int responseCode) {
        enterErrorState(Localization.get("post.redirection.error", responseCode + ""));
    }

    @Override
    public void processClientError(int responseCode) {
        enterErrorState(Localization.get("post.client.error", responseCode + ""));
    }

    @Override
    public void processServerError(int responseCode) {
        enterErrorState(Localization.get("post.server.error", responseCode + ""));
    }

    @Override
    public void processOther(int responseCode) {
        enterErrorState(Localization.get("post.unknown.response", responseCode + ""));
    }

    @Override
    public void handleIOException(IOException exception) {
        enterErrorState(Localization.get("post.io.error", exception.getMessage()));
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
            case SimpleHttpTask.SIMPLE_HTTP_TASK_ID:
                title = Localization.get("query.dialog.title");
                message = Localization.get("query.dialog.body");
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in CommCareHomeActivity");
                return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }
}
