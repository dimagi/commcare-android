package org.commcare.fragments.connect;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import org.commcare.activities.connect.ConnectActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.fragments.base.BaseConnectFragment;

import java.util.Objects;

public abstract class ConnectJobFragment<T extends ViewBinding> extends BaseConnectFragment<T> {
    protected ConnectJobRecord job;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        reloadActiveJob();
    }

    protected void setActiveJob(ConnectJobRecord updatedJob) {
        job = updatedJob;
        ((ConnectActivity) requireActivity()).setActiveJob(updatedJob);
    }

    protected void reloadActiveJob() {
        job = ((ConnectActivity) requireActivity()).getActiveJob();
        Objects.requireNonNull(job);
    }

    /** Opens this opportunity's learn or delivery app, installing it first if need be. */
    protected void launchApp(boolean isLearning) {
        launchApp(job, isLearning);
    }

    @Override
    public String getEndpoint() {
        return null;
    }
}
