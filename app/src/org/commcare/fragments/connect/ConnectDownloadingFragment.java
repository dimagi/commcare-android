package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.android.database.connect.models.ConnectJobLearningModule;
import org.commcare.dalvik.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.fragment.app.Fragment;

public class ConnectDownloadingFragment extends Fragment {
    public ConnectDownloadingFragment() {
        // Required empty public constructor
    }

    public static ConnectDownloadingFragment newInstance() {
        return new ConnectDownloadingFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConnectDownloadingFragmentArgs args = ConnectDownloadingFragmentArgs.fromBundle(getArguments());
        getActivity().setTitle(args.getAppTitle());

        View view = inflater.inflate(R.layout.fragment_connect_downloading, container, false);

        TextView textView = view.findViewById(R.id.connect_downloading_title);
        textView.setText(args.getTitle());

        textView = view.findViewById(R.id.connect_downloading_status);
        textView.setText(getString(R.string.connect_downloading_status, 0));

        return view;
    }
}
