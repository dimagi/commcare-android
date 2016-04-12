package org.commcare.activities;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.interfaces.ConnectorWithMessaging;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.SimpleHttpTask;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;

import java.io.InputStream;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SyncRequestActivity
        extends CommCareActivity<SyncRequestActivity>
        implements ConnectorWithMessaging<SyncRequestActivity>{
    private static final String TAG = SyncRequestActivity.class.getSimpleName();
    private static final int SELECT_QUERY_RESULT = 0;

    private static final String TASK_LAUNCHED_KEY = "task-launched-key";
    private static final String IN_ERROR_STATE_KEY = "in-error-state-key";

    private boolean hasTaskLaunched;
    private boolean inErrorState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        resetQuerySessionFromBundle(savedInstanceState);

        if (inErrorState) {
            showErrorState();
        } else {
            makePostRequest();
        }
    }

    private void resetQuerySessionFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            hasTaskLaunched = savedInstanceState.getBoolean(TASK_LAUNCHED_KEY);
            inErrorState = savedInstanceState.getBoolean(IN_ERROR_STATE_KEY);
        }
    }

    private void makePostRequest() {
        if (!hasTaskLaunched && !inErrorState) {
            SimpleHttpTask<SyncRequestActivity> syncTask = buildSyncTask();
            syncTask.connect(this);
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
        // TODO PLM: launch sync task; run following upon successful completion
        complete();
    }

    private void showErrorState() {
        inErrorState = true;
    }

    private void retryPost() {
        inErrorState = false;
        hasTaskLaunched = false;
        makePostRequest();
    }

    private void complete() {
        CommCareApplication._().getCurrentSessionWrapper().terminateSession();
    }

    private SimpleHttpTask<SyncRequestActivity> buildSyncTask() {
        return new SimpleHttpTask<SyncRequestActivity>("", "") {
            @Override
            protected void deliverResult(SyncRequestActivity receiver, InputStream result) {
                receiver.performSync();
            }

            @Override
            protected void deliverUpdate(SyncRequestActivity receiver, Integer[] updates) {

            }

            @Override
            protected void deliverError(SyncRequestActivity receiver, Exception e) {
                showErrorState();
            }
        };
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
                title = Localization.get("sync.progress.title");
                message = Localization.get("sync.progress.purge");
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in activity");
                return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }

    @Override
    public void displayMessage(String message) {
        Log.d(TAG, message);
    }

    @Override
    public void displayBadMessage(String message) {
        Log.d(TAG, "BAD: " + message);

    }

    @Override
    public void displayBadMessageWithoutToast(String message) {
        Log.d(TAG, "BAD NO TOAST: " + message);
    }
}
