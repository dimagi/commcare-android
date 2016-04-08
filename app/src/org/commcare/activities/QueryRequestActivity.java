package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.RemoteQuerySessionManager;
import org.commcare.suite.model.RemoteQueryDatum;
import org.commcare.suite.model.SessionDatum;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
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
        LinearLayout item = (LinearLayout) findViewById(R.id.query_prompts);
        for (String promptKey : remoteQuerySessionManager.getNeededUserInputDisplays().keySet()) {
            EditText prompt = new EditText(getApplicationContext());
            prompt.setHint(promptKey);
            item.addView(prompt);

            promptsBoxes.put(promptKey, prompt);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
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
        Hashtable<String, String> answeredPrompts =
                (Hashtable<String, String>) savedInstanceState.getSerializable(ANSWERED_USER_PROMPTS_KEY);
        if (answeredPrompts != null) {
            for (Map.Entry<String, String> entry : answeredPrompts.entrySet()) {
                remoteQuerySessionManager.answerUserPrompt(entry.getKey(), entry.getValue());
            }
        }
    }
}
