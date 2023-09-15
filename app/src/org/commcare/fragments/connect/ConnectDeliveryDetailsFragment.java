package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.dalvik.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

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
        ConnectJob job = ConnectDeliveryDetailsFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_delivery_details, container, false);

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

        TextView textView = view.findViewById(R.id.connect_delivery_begin_text);
        textView.setText(getString(R.string.connect_delivery_last_begin, df.format(job.getBeginDeadline())));

        textView = view.findViewById(R.id.connect_delivery_max_visits_text);
        textView.setText(getString(R.string.connect_delivery_max_visits, job.getMaxVisits()));

        textView = view.findViewById(R.id.connect_delivery_end_text);
        textView.setText(getString(R.string.connect_delivery_project_ends, df.format(job.getProjectEndDate())));

        textView = view.findViewById(R.id.connect_delivery_max_daily_visits_text);
        textView.setText(getString(R.string.connect_delivery_max_daily_visits, job.getMaxDailyVisits()));

        Button button = view.findViewById(R.id.connect_delivery_button);
        button.setOnClickListener(v -> {
            String title = getString(R.string.connect_downloading_delivery);
            NavDirections directions = ConnectDeliveryDetailsFragmentDirections.actionConnectJobDeliveryDetailsFragmentToConnectDownloadingFragment(title, job.getTitle());
            Navigation.findNavController(button).navigate(directions);
        });

        return view;
    }
}
