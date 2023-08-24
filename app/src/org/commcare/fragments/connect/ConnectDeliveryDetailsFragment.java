package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.dalvik.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.fragment.app.Fragment;

/**
 * Fragment for showing delivery details for a Connect job
 *
 * @author dviggiano
 */
public class ConnectDeliveryDetailsFragment extends Fragment {
    public ConnectDeliveryDetailsFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryDetailsFragment newInstance() {
        return new ConnectDeliveryDetailsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConnectJob job = ConnectDeliveryDetailsFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_delivery_details, container, false);

        TextView textView = view.findViewById(R.id.connect_delivery_title);
        textView.setText(getString(R.string.connect_delivery_review_title));

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

        textView = view.findViewById(R.id.connect_delivery_begin_text);
        textView.setText(getString(R.string.connect_delivery_last_begin, df.format(job.getBeginDeadline())));

        textView = view.findViewById(R.id.connect_delivery_max_visits_text);
        textView.setText(getString(R.string.connect_delivery_max_visits, job.getMaxVisits()));

        textView = view.findViewById(R.id.connect_delivery_end_text);
        textView.setText(getString(R.string.connect_delivery_project_ends, df.format(job.getProjectEndDate())));

        textView = view.findViewById(R.id.connect_delivery_max_daily_visits_text);
        textView.setText(getString(R.string.connect_delivery_max_daily_visits, job.getMaxDailyVisits()));

        textView = view.findViewById(R.id.connect_delivery_action_title);
        textView.setText(getString(R.string.connect_delivery_ready_to_claim));

        textView = view.findViewById(R.id.connect_delivery_action_details);
        textView.setText(getString(R.string.connect_delivery_ready_to_claim_detailed));

        Button button = view.findViewById(R.id.connect_delivery_button);
        button.setOnClickListener(v -> Toast.makeText(getContext(), "Not ready yet...", Toast.LENGTH_SHORT).show());

        return view;
    }
}
