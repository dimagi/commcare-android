package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.dalvik.R;

import androidx.fragment.app.Fragment;

/**
 * Fragment for showing delivery progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectDeliveryProgressFragment extends Fragment {
    public ConnectDeliveryProgressFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressFragment newInstance() {
        return new ConnectDeliveryProgressFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //ConnectJob job = ConnectJobIntroFragmentArgs.fromBundle(getArguments()).getJob();
        //getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_delivery_progress, container, false);

        //TextView textView = view.findViewById(R.id.connect_job_intro_title);
        //textView.setText(job.getTitle());

        return view;
    }
}
