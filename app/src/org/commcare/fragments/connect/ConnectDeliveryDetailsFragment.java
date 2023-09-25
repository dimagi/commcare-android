package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

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
        ConnectJobRecord job = ConnectDeliveryDetailsFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_delivery_details, container, false);

        TextView textView = view.findViewById(R.id.connect_delivery_total_visits_text);
        int maxPossibleVisits = job.getMaxPossibleVisits();
        textView.setText(getString(R.string.connect_delivery_max_visits, maxPossibleVisits));

        textView = view.findViewById(R.id.connect_delivery_remaining_text);
        int daysRemaining = job.getDaysRemaining();
        textView.setText(getString(R.string.connect_delivery_days_remaining, daysRemaining));

        textView = view.findViewById(R.id.connect_delivery_max_daily_text);
        textView.setText(getString(R.string.connect_delivery_max_daily_visits, job.getMaxDailyVisits()));

        textView = view.findViewById(R.id.connect_delivery_budget_text);
        textView.setText(getString(R.string.connect_delivery_earn, maxPossibleVisits * job.getBudgetPerVisit(), maxPossibleVisits));

        boolean expired = daysRemaining < 0;
        textView = view.findViewById(R.id.connect_delivery_action_title);
        textView.setText(expired ? R.string.connect_delivery_expired : R.string.connect_delivery_ready_to_claim);

        textView = view.findViewById(R.id.connect_delivery_action_details);
        textView.setText(expired ? R.string.connect_delivery_expired_detailed : R.string.connect_delivery_ready_to_claim_detailed);

        Button button = view.findViewById(R.id.connect_delivery_button);
        button.setEnabled(!expired);
        button.setOnClickListener(v -> {
            String title = getString(R.string.connect_downloading_delivery);
            NavDirections directions = ConnectDeliveryDetailsFragmentDirections.actionConnectJobDeliveryDetailsFragmentToConnectDownloadingFragment(title, job);
            Navigation.findNavController(button).navigate(directions);
        });

        return view;
    }
}
