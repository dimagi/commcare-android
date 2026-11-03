package org.commcare.activities;

import androidx.annotation.NonNull;

import org.commcare.home.SyncDelegate;
import org.commcare.home.SyncHost;
import org.commcare.tasks.DataPullTask;
import org.commcare.views.dialogs.CustomProgressDialog;

public abstract class SyncCapableCommCareActivity<T> extends SessionAwareCommCareActivity<T>
        implements SyncHost {

    protected abstract SyncDelegate getSyncDelegate();

    @NonNull
    @Override
    public CommCareActivity<?> getSyncConnector() {
        return this;
    }

    @Override
    public void onPullTaskFinished(@NonNull DataPullTask.PullTaskResult result) {
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return getSyncDelegate().generateProgressDialog(taskId);
    }
}
