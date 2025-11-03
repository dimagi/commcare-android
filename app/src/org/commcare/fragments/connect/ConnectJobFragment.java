package org.commcare.fragments.connect;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import org.commcare.activities.connect.ConnectActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.fragments.base.BaseFragment;

import java.util.Objects;

public abstract class ConnectJobFragment<T extends ViewBinding> extends BaseFragment<T> {
    protected ConnectJobRecord job;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        job = ((ConnectActivity)requireActivity()).getActiveJob();
        Objects.requireNonNull(job);
    }
}
