package org.commcare.fragments.connect;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.commcare.activities.connect.ConnectActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;

import java.util.Objects;

public class ConnectJobFragment extends Fragment {
    protected ConnectJobRecord job;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        job = ((ConnectActivity)requireActivity()).getActiveJob();
        Objects.requireNonNull(job);
    }
}
