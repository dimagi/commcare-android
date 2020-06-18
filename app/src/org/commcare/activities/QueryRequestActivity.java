package org.commcare.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;

import org.commcare.CommCareApplication;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.modern.util.Pair;
import org.commcare.session.RemoteQuerySessionManager;
import org.commcare.suite.model.DisplayData;
import org.commcare.suite.model.DisplayUnit;
import org.commcare.suite.model.QueryPrompt;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.ViewUtil;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.core.util.OrderedHashtable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import static org.commcare.activities.EntitySelectActivity.BARCODE_FETCH;

/**
 * Collects 'query datum' in the current session. Prompts user for query
 * params, makes query to server and stores xml 'fixture' response into current
 * session. Allows for 'case search and claim' workflow when used inside a
 * 'remote-request' entry in conjuction with entity select datum and sync
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
    private static final CharSequence SELECT1 = "select1";
    private static final String APPEARANCE_BARCODE_SCAN = "barcode_scan";

    @UiElement(value = R.id.request_button, locale = "query.button")
    private Button queryButton;

    @UiElement(value = R.id.error_message)
    private TextView errorTextView;

    private boolean inErrorState;
    private String errorMessage;
    private RemoteQuerySessionManager remoteQuerySessionManager;
    private final Hashtable<String, View> promptsBoxes = new Hashtable<>();
    private String mPendingPromptId;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        AndroidSessionWrapper sessionWrapper = CommCareApplication.instance().getCurrentSessionWrapper();
        remoteQuerySessionManager =
                RemoteQuerySessionManager.buildQuerySessionManager(sessionWrapper.getSession(),
                        sessionWrapper.getEvaluationContext());

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

        queryButton.setOnClickListener(v -> {
            ViewUtil.hideVirtualKeyboard(QueryRequestActivity.this);
            answerPrompts();
            makeQueryRequest();
        });

        if (inErrorState) {
            enterErrorState();
        }
    }

    private void buildPromptUI() {
        LinearLayout promptsLayout = findViewById(R.id.query_prompts);
        OrderedHashtable<String, QueryPrompt> userInputDisplays =
                remoteQuerySessionManager.getNeededUserInputDisplays();
        int promptCount = 1;

        for (Enumeration en = userInputDisplays.keys(); en.hasMoreElements(); ) {
            String promptId = (String)en.nextElement();
            boolean isLastPrompt = promptCount++ == userInputDisplays.size();
            buildPromptEntry(promptsLayout, promptId,
                    userInputDisplays.get(promptId), isLastPrompt);
        }
    }

    private void buildPromptEntry(LinearLayout promptsLayout, String promptId,
                                  QueryPrompt queryPrompt, boolean isLastPrompt) {
        Hashtable<String, String> userAnswers =
                remoteQuerySessionManager.getUserAnswers();

        View promptView = LayoutInflater.from(this).inflate(R.layout.query_prompt_layout, promptsLayout, false);
        setLabelText(promptView, queryPrompt.getDisplay());
        View inputView;
        String input = queryPrompt.getInput();
        if (input != null && input.contentEquals(SELECT1)) {
            inputView = setUpSpinnerView(promptView, queryPrompt, userAnswers);
        } else {
            inputView = setUpEditTextView(promptView, queryPrompt, promptId, userAnswers, isLastPrompt);
        }
        setUpBarCodeScanButton(promptView, promptId, queryPrompt);

        promptsLayout.addView(promptView);
        promptsBoxes.put(promptId, inputView);
    }

    private void setUpBarCodeScanButton(View promptView, String promptId, QueryPrompt queryPrompt) {
        ImageView barcodeScannerView = promptView.findViewById(R.id.barcode_scanner);
        barcodeScannerView.setVisibility(isBarcodeEnabled(queryPrompt) ? View.VISIBLE : View.INVISIBLE);
        barcodeScannerView.setTag(promptId);
        barcodeScannerView.setOnClickListener(v ->
                callBarcodeScanIntent((String)v.getTag())
        );
    }

    private Spinner setUpSpinnerView(View promptView, QueryPrompt queryPrompt,
                                     Hashtable<String, String> userAnswers) {
        Spinner promptSpinner = promptView.findViewById(R.id.prompt_spinner);
        promptSpinner.setVisibility(View.VISIBLE);
        promptView.findViewById(R.id.prompt_et).setVisibility(View.GONE);

        promptSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Vector<SelectChoice> choices = queryPrompt.getItemsetBinding().getChoices();
                SelectChoice selectChoice = choices.get(position);
                String oldAnswer = userAnswers.get(queryPrompt.getKey());
                if(oldAnswer == null || !oldAnswer.contentEquals(selectChoice.getValue())) {
                    remoteQuerySessionManager.answerUserPrompt(queryPrompt.getKey(), selectChoice.getValue());
                    refreshUI();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        setSpinnerData(userAnswers, queryPrompt, promptSpinner);
        return promptSpinner;
    }

    private void setSpinnerData(Hashtable<String, String> userAnswers, QueryPrompt queryPrompt, Spinner promptSpinner) {
        remoteQuerySessionManager.populateItemSetChoices(queryPrompt);
        Vector<SelectChoice> items = queryPrompt.getItemsetBinding().getChoices();
        String answer = userAnswers.get(queryPrompt.getKey());
        String[] choices = new String[items.size()];
        int selectedPosition = -1;
        for (int i = 0; i < items.size(); i++) {
            SelectChoice item = items.get(i);
            choices[i] = item.getLabelInnerText();
            if(item.getValue().equals(answer)){
                selectedPosition = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        promptSpinner.setAdapter(adapter);
        if(selectedPosition != -1) {
            promptSpinner.setSelection(selectedPosition);
        }
    }

    private void refreshUI() {
        for (Map.Entry<String, View> promptEntry : promptsBoxes.entrySet()) {
            View input = promptEntry.getValue();
            if (input instanceof Spinner) {
                String key = promptEntry.getKey();
                setSpinnerData(remoteQuerySessionManager.getUserAnswers(),
                        remoteQuerySessionManager.getNeededUserInputDisplays().get(key),
                        ((Spinner)input));
            }
        }
    }

    private EditText setUpEditTextView(View promptView, QueryPrompt queryPrompt, String promptId,
                                       Hashtable<String, String> userAnswers, boolean isLastPrompt) {
        EditText promptEditText = promptView.findViewById(R.id.prompt_et);
        promptEditText.setVisibility(View.VISIBLE);
        promptView.findViewById(R.id.prompt_spinner).setVisibility(View.GONE);

        if (userAnswers.containsKey(promptId)) {
            promptEditText.setText(userAnswers.get(promptId));
        }
        // needed to allow 'done' and 'next' keyboard action
        if (isLastPrompt) {
            promptEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        } else {
            // replace 'done' on keyboard with 'next'
            promptEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        }
        return promptEditText;
    }

    private boolean isBarcodeEnabled(QueryPrompt queryPrompt) {
        return APPEARANCE_BARCODE_SCAN.equals(queryPrompt.getAppearance());
    }

    private void callBarcodeScanIntent(String promptId) {
        Intent intent = new IntentIntegrator(this).createScanIntent();
        mPendingPromptId = promptId;
        try {
            startActivityForResult(intent, BARCODE_FETCH);
        } catch (ActivityNotFoundException anfe) {
            Toast.makeText(this,
                    "No barcode reader available! You can install one " +
                            "from the android market.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == BARCODE_FETCH) {
            if (resultCode == RESULT_OK && !TextUtils.isEmpty(mPendingPromptId)) {
                String result = intent.getStringExtra("SCAN_RESULT");
                if (result != null) {
                    result = result.trim();
                    View input = promptsBoxes.get(mPendingPromptId);
                    if (input instanceof EditText) {
                        ((EditText)input).setText(result);
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    private void setLabelText(View promptView, DisplayUnit display) {
        DisplayData displayData = display.evaluate();
        String promptText =
                Localizer.processArguments(displayData.getName(), new String[]{""}).trim();
        ((TextView)promptView.findViewById(R.id.prompt_label)).setText(promptText);
    }

    private void answerPrompts() {
        remoteQuerySessionManager.clearAnswers();
        for (Map.Entry<String, View> promptEntry : promptsBoxes.entrySet()) {
            View input = promptEntry.getValue();
            String promptText = getPromptInput(input);
            if (!"".equals(promptText)) {
                remoteQuerySessionManager.answerUserPrompt(promptEntry.getKey(), promptText);
            }
        }
    }

    private String getPromptInput(View input) {
        if (input instanceof EditText) {
            return ((EditText)input).getText().toString();
        } else if (input instanceof Spinner) {
            return ((Spinner)input).getSelectedItem().toString();
        }
        return null;
    }

    private void makeQueryRequest() {
        clearErrorState();
        ModernHttpTask httpTask = new ModernHttpTask(this,
                remoteQuerySessionManager.getBaseUrl().toString(),
                new HashMap(remoteQuerySessionManager.getRawQueryParams()),
                new HashMap(),
                new AuthInfo.CurrentAuth());
        httpTask.connect((CommCareTaskConnector)this);
        httpTask.executeParallel();
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

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        answerPrompts();

        savedInstanceState.putSerializable(ANSWERED_USER_PROMPTS_KEY,
                remoteQuerySessionManager.getUserAnswers());
        savedInstanceState.putString(ERROR_MESSAGE_KEY, errorMessage);
        savedInstanceState.putBoolean(IN_ERROR_STATE_KEY, inErrorState);
    }

    @Override
    public void processSuccess(int responseCode, InputStream responseData) {
        Pair<ExternalDataInstance, String> instanceOrError =
                remoteQuerySessionManager.buildExternalDataInstance(responseData);
        if (instanceOrError.first == null) {
            enterErrorState(Localization.get("query.response.format.error",
                    instanceOrError.second));
        } else if (isResponseEmpty(instanceOrError.first)) {
            Toast.makeText(this, Localization.get("query.response.empty"), Toast.LENGTH_SHORT).show();
        } else {
            CommCareApplication.instance().getCurrentSession().setQueryDatum(instanceOrError.first);
            setResult(RESULT_OK);
            finish();
        }
    }

    private boolean isResponseEmpty(ExternalDataInstance instance) {
        return !instance.getRoot().hasChildren();
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
        if (exception instanceof AuthenticationInterceptor.PlainTextPasswordException) {
            enterErrorState(Localization.get("auth.request.not.using.https", remoteQuerySessionManager.getBaseUrl().toString()));
        } else if (exception instanceof IOException) {
            enterErrorState(Localization.get("post.io.error", exception.getMessage()));
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
            case ModernHttpTask.SIMPLE_HTTP_TASK_ID:
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
