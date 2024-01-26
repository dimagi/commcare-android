package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.connect.ConnectDatabaseHelper;
import org.commcare.activities.connect.ConnectNetworkHelper;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.MultipleAppsUtil;

import java.io.IOException;
import java.io.InputStream;

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
        String moneyValue = job.getMoneyString(maxPossibleVisits * job.getBudgetPerVisit());
        textView.setText(getString(R.string.connect_delivery_earn, moneyValue, maxPossibleVisits));

        boolean expired = daysRemaining < 0;
        textView = view.findViewById(R.id.connect_delivery_action_title);
        textView.setText(expired ? R.string.connect_delivery_expired : R.string.connect_delivery_ready_to_claim);

        textView = view.findViewById(R.id.connect_delivery_action_details);
        textView.setText(expired ? R.string.connect_delivery_expired_detailed : R.string.connect_delivery_ready_to_claim_detailed);

        boolean jobClaimed = job.getStatus() == ConnectJobRecord.STATUS_DELIVERING;
        boolean installed = false;
        for (ApplicationRecord app : MultipleAppsUtil.appRecordArray()) {
            if (job.getDeliveryAppInfo().getAppId().equals(app.getUniqueId())) {
                installed = true;
                break;
            }
        }
        final boolean appInstalled = installed;

        int buttonTextId = jobClaimed ? (appInstalled ? R.string.connect_delivery_go : R.string.connect_delivery_get_app) : R.string.connect_delivery_claim;

        Button button = view.findViewById(R.id.connect_delivery_button);
        button.setText(buttonTextId);
        button.setEnabled(!expired);
        button.setOnClickListener(v -> {
            if(jobClaimed) {
                proceedAfterJobClaimed(button, job, appInstalled);
            } else {
                //Claim job
                ConnectNetworkHelper.claimJob(getContext(), job.getJobId(), new ConnectNetworkHelper.INetworkResultHandler() {
                    @Override
                    public void processSuccess(int responseCode, InputStream responseData) {
                        proceedAfterJobClaimed(button, job, appInstalled);
                        reportApiCall(true);
                    }

                    @Override
                    public void processFailure(int responseCode, IOException e) {
                        Toast.makeText(getContext(), "Connect: error claming job", Toast.LENGTH_SHORT).show();
                        reportApiCall(false);
                    }

                    @Override
                    public void processNetworkFailure() {
                        Toast.makeText(getContext(), getString(R.string.recovery_network_unavailable),
                                Toast.LENGTH_SHORT).show();
                        reportApiCall(false);
                    }
                });
            }
        });

        return view;
    }

    private void reportApiCall(boolean success) {
        FirebaseAnalyticsUtil.reportCccApiClaimJob(success);
    }

    private void proceedAfterJobClaimed(Button button, ConnectJobRecord job, boolean installed) {
        job.setStatus(ConnectJobRecord.STATUS_DELIVERING);
        ConnectDatabaseHelper.upsertJob(getContext(), job);

        NavDirections directions;
        if (installed) {
            directions = ConnectDeliveryDetailsFragmentDirections.actionConnectJobDeliveryDetailsFragmentToConnectJobDeliveryProgressFragment(job);
        } else {
            String title = getString(R.string.connect_downloading_delivery);
            directions = ConnectDeliveryDetailsFragmentDirections.actionConnectJobDeliveryDetailsFragmentToConnectDownloadingFragment(title, false, false, job);
        }

        Navigation.findNavController(button).navigate(directions);
    }
}
