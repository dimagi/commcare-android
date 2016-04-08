package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.RemoteQuerySessionManager;
import org.commcare.suite.model.DisplayData;
import org.commcare.suite.model.DisplayUnit;
import org.commcare.suite.model.RemoteQueryDatum;
import org.commcare.suite.model.SessionDatum;
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
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Map;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@ManagedUi(R.layout.query_request_layout)
public class QueryRequestActivity extends CommCareActivity<QueryRequestActivity> {
    private static final String TAG = QueryRequestActivity.class.getSimpleName();
    private static final String ANSWERED_USER_PROMPTS_KEY = "answered_user_prompts";

    private RemoteQuerySessionManager remoteQuerySessionManager;
    private Hashtable<String, EditText> promptsBoxes = new Hashtable<>();

    @UiElement(value = R.id.query_button, locale = "query.button")
    private Button queryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        remoteQuerySessionManager =
                buildQuerySessionManager(CommCareApplication._().getCurrentSessionWrapper());

        if (remoteQuerySessionManager == null) {
            Log.e(TAG, "Tried to launch remote query activity at wrong time in session.");
            finish();
        } else {
            resetQuerySessionFromBundle(savedInstanceState);
        }

        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                answerPrompts();
                makeQueryRequest();
            }
        });
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
        Log.d(TAG, remoteQuerySessionManager.buildQueryUrl());
        TreeElement root = null;
        String instanceId = "";
        try {
            InputStream is = getAssets().open("patients.xml");
            root = new TreeElementParser(ElementParser.instantiateParser(is), 0, instanceId).parse();
        } catch (InvalidStructureException | IOException
                | XmlPullParserException | UnfullfilledRequirementsException e ){

        }
        ExternalDataInstance instance = ExternalDataInstance.buildFromRemote(instanceId, root);
        CommCareApplication._().getCurrentSession().setQueryDatum(instance);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putSerializable(ANSWERED_USER_PROMPTS_KEY,
                remoteQuerySessionManager.getUserAnswers());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    }

    @Override
    protected void onResume() {
        super.onResume();

        buildPromptUI();
    }

    private void buildPromptUI() {
        LinearLayout promptsLayout = (LinearLayout) findViewById(R.id.query_prompts);
        for (Map.Entry<String, DisplayUnit> displayEntry : remoteQuerySessionManager.getNeededUserInputDisplays().entrySet()) {
            promptsLayout.addView(createPromptEntry(displayEntry.getValue()));

            EditText promptEditText= new EditText(this);
            promptsLayout.addView(promptEditText);
            promptsBoxes.put(displayEntry.getKey(), promptEditText);
        }

    }

    private static RemoteQuerySessionManager buildQuerySessionManager(AndroidSessionWrapper sessionWrapper) {
        SessionDatum datum = sessionWrapper.getSession().getNeededDatum();
        if (datum instanceof RemoteQueryDatum) {
            return new RemoteQuerySessionManager((RemoteQueryDatum) datum, sessionWrapper.getEvaluationContext());
        } else {
            return null;
        }
    }

    private void resetQuerySessionFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Hashtable<String, String> answeredPrompts =
                    (Hashtable<String, String>) savedInstanceState.getSerializable(ANSWERED_USER_PROMPTS_KEY);
            if (answeredPrompts != null) {
                for (Map.Entry<String, String> entry : answeredPrompts.entrySet()) {
                    remoteQuerySessionManager.answerUserPrompt(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private MediaLayout createPromptEntry(DisplayUnit display) {
        DisplayData mData = display.evaluate();
        String str = Localizer.processArguments(mData.getName(), new String[]{""}).trim();
        TextView text = new TextView(getApplicationContext());
        text.setText(str);

        int padding = (int)getResources().getDimension(R.dimen.help_text_padding);
        text.setPadding(0, 0, 0, 7);

        MediaLayout helpLayout = new MediaLayout(this);
        helpLayout.setAVT(text, mData.getAudioURI(), mData.getImageURI(), null, null);
        helpLayout.setPadding(padding, padding, padding, padding);

        return helpLayout;
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
            case 1:
                title = Localization.get("sync.progress.title");
                message = Localization.get("sync.progress.purge");
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in CommCareHomeActivity");
                return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }
}
