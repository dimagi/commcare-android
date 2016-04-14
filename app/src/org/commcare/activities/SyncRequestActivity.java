package org.commcare.activities;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.interfaces.ConnectorWithResultCallback;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.SimpleHttpTask;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SyncRequestActivity
        extends CommCareActivity<SyncRequestActivity>
        implements ConnectorWithHttpResponseProcessor<SyncRequestActivity>,
        ConnectorWithResultCallback<SyncRequestActivity> {
    private static final String TAG = SyncRequestActivity.class.getSimpleName();

    private static final String TASK_LAUNCHED_KEY = "task-launched-key";
    private static final String IN_ERROR_STATE_KEY = "in-error-state-key";

    public static final String URL_KEY = "url-key";
    public static final String PARAMS_KEY = "params-key";

    private URL url;
    private Hashtable<String, String> params;

    private boolean hasTaskLaunched;
    private boolean inErrorState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadStateFromSavedInstance(savedInstanceState);
        loadStateFromIntent(getIntent());

        if (inErrorState) {
            showErrorState();
        } else {
            makePostRequest();
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

    private void loadStateFromSavedInstance(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            hasTaskLaunched = savedInstanceState.getBoolean(TASK_LAUNCHED_KEY);
            inErrorState = savedInstanceState.getBoolean(IN_ERROR_STATE_KEY);
        }
    }

    private void makePostRequest() {
        if (!hasTaskLaunched && !inErrorState) {
            SimpleHttpTask syncTask = new SimpleHttpTask(this, url, params, true);
            syncTask.connect((ConnectorWithHttpResponseProcessor)this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                syncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                syncTask.execute();
            }
            hasTaskLaunched = true;
        }
    }

    private void performSync() {
        Log.d(TAG, "perform sync");

        FormAndDataSyncer formAndDataSyncer = new FormAndDataSyncer(this, this);
        formAndDataSyncer.syncData(false, false);
    }

    private void showErrorState() {
        inErrorState = true;
    }

    private void retryPost() {
        inErrorState = false;
        hasTaskLaunched = false;
        makePostRequest();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean(TASK_LAUNCHED_KEY, hasTaskLaunched);
        savedInstanceState.putBoolean(IN_ERROR_STATE_KEY, inErrorState);
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

    @Override
    public void reportSuccess(String message) {
        Log.d(TAG, message);

        CommCareApplication._().getCurrentSessionWrapper().terminateSession();

        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void reportFailure(String message, boolean showPopupNotification) {
        if (showPopupNotification) {
            Log.d(TAG, "BAD: " + message);
        } else {
            Log.d(TAG, "BAD NO TOAST: " + message);
        }
        showErrorState();
    }

    @Override
    public void processSuccess(int responseCode, InputStream responseData) {
        performSync();
    }

    @Override
    public void processRedirection(int responseCode) {
        showErrorState();
    }

    @Override
    public void processClientError(int responseCode) {
        showErrorState();
    }

    @Override
    public void processServerError(int responseCode) {
        showErrorState();
    }

    @Override
    public void processOther(int responseCode) {
        showErrorState();
    }

    @Override
    public void handleIOException(IOException exception) {
        showErrorState();
    }
}
