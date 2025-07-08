package org.commcare.fragments.connect;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectJobHelper;

import java.util.Objects;

public class ConnectJobFragment extends Fragment {
    protected ConnectJobRecord job;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        job = ConnectJobHelper.INSTANCE.getActiveJob();
        Objects.requireNonNull(job);
    }
}
