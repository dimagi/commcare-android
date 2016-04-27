package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.interfaces.ConnectorWithResultCallback;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.SimpleHttpTask;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;

/**
 * Perform post request to external server and trigger a sync upon success.
 * Can be used to perform server transactions, to, for instance, claim a case
 * and pull it down to the mobile device
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@ManagedUi(R.layout.http_request_layout)
public class PostRequestActivity
        extends CommCareActivity<PostRequestActivity>
        implements ConnectorWithHttpResponseProcessor<PostRequestActivity>,
        ConnectorWithResultCallback<PostRequestActivity> {
    private static final String TAG = PostRequestActivity.class.getSimpleName();

    private static final String TASK_LAUNCHED_KEY = "task-launched-key";
    private static final String IN_ERROR_STATE_KEY = "in-error-state-key";
    private static final String ERROR_MESSAGE_KEY = "error-message-key";

    public static final String URL_KEY = "url-key";
    public static final String PARAMS_KEY = "params-key";

    @UiElement(value = R.id.request_button, locale = "post.request.button")
    private Button retryButton;

    @UiElement(value = R.id.error_message)
    private TextView errorMessageBox;

    private URL url;
    private Hashtable<String, String> params;
    private String errorMessage;
    private boolean hasTaskLaunched;
    private boolean inErrorState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadStateFromSavedInstance(savedInstanceState);
        loadStateFromIntent(getIntent());

        setupUI();

        if (!inErrorState) {
            makePostRequest();
        }
    }

    private void loadStateFromSavedInstance(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            hasTaskLaunched = savedInstanceState.getBoolean(TASK_LAUNCHED_KEY);
            inErrorState = savedInstanceState.getBoolean(IN_ERROR_STATE_KEY);
            errorMessage = savedInstanceState.getString(ERROR_MESSAGE_KEY);
        }
    }

    private void loadStateFromIntent(Intent intent) {
        if (intent.hasExtra(URL_KEY) && intent.hasExtra(PARAMS_KEY)) {
            try {
                url = new URL(intent.getStringExtra(URL_KEY));
            } catch (MalformedURLException e) {
                inErrorState = true;
            }
            params = (Hashtable<String, String>)intent.getSerializableExtra(PARAMS_KEY);
        } else {
            inErrorState = true;
        }
    }

    private void setupUI() {
        if (inErrorState) {
            enterErrorState();
        } else {
            retryButton.setVisibility(View.GONE);
        }

        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retryPost();
            }
        });
    }

    private void performSync() {
        Log.d(TAG, "perform sync");

        FormAndDataSyncer formAndDataSyncer = new FormAndDataSyncer();
        formAndDataSyncer.syncData(this, this, false, false);
    }

    private void makePostRequest() {
        if (!hasTaskLaunched && !inErrorState) {
            SimpleHttpTask syncTask = new SimpleHttpTask(this, url, params, true);
            syncTask.connect((ConnectorWithHttpResponseProcessor)this);
            syncTask.executeParallel();
            hasTaskLaunched = true;
        }
    }

    private void enterErrorState() {
        inErrorState = true;
        errorMessageBox.setVisibility(View.VISIBLE);
        errorMessageBox.setText(errorMessage);
        retryButton.setVisibility(View.VISIBLE);
    }

    private void enterErrorState(String message) {
        errorMessage = message;
        enterErrorState();
    }

    private void retryPost() {
        inErrorState = false;
        errorMessage = "";
        errorMessageBox.setVisibility(View.GONE);
        hasTaskLaunched = false;
        makePostRequest();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean(TASK_LAUNCHED_KEY, hasTaskLaunched);
        savedInstanceState.putBoolean(IN_ERROR_STATE_KEY, inErrorState);
        savedInstanceState.putString(ERROR_MESSAGE_KEY, errorMessage);
    }

    @Override
    public void reportSuccess(String message) {
        Log.d(TAG, "sync successful");
        Log.d(TAG, message);

        CommCareApplication._().getCurrentSessionWrapper().terminateSession();

        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void reportFailure(String message, boolean showPopupNotification) {
        Log.d(TAG, "sync failed");
        if (showPopupNotification) {
            Log.d(TAG, "BAD: " + message);
        } else {
            Log.d(TAG, "BAD NO TOAST: " + message);
        }
        enterErrorState("sync failed");
    }

    @Override
    public void processSuccess(int responseCode, InputStream responseData) {
        performSync();
    }

    @Override
    public void processRedirection(int responseCode) {
        enterErrorState(responseCode + "");
    }

    @Override
    public void processClientError(int responseCode) {
        enterErrorState(responseCode + "");
    }

    @Override
    public void processServerError(int responseCode) {
        enterErrorState(responseCode + "");
    }

    @Override
    public void processOther(int responseCode) {
        enterErrorState(responseCode + "");
    }

    @Override
    public void handleIOException(IOException exception) {
        enterErrorState(exception.getMessage());
    }

    @Override
    public void onBackPressed() {
        if (inErrorState) {
            finish();
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
            case DataPullTask.DATA_PULL_TASK_ID:
                title = Localization.get("sync.progress.title");
                message = Localization.get("sync.progress.purge");
                break;
            case SimpleHttpTask.SIMPLE_HTTP_TASK_ID:
                title = "sending post";
                message = "sending post";
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in activity");
                return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }

}
