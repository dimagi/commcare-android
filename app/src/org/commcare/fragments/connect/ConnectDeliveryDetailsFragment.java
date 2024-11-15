package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.views.connect.RoundedButton;
import org.commcare.views.connect.connecttextview.ConnectBoldTextView;
import org.commcare.views.connect.connecttextview.ConnectMediumTextView;
import org.commcare.views.connect.connecttextview.ConnectRegularTextView;

import java.io.IOException;
import java.io.InputStream;

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
        ConnectJobRecord job = ConnectManager.getActiveJob();
        ConnectDeliveryDetailsFragmentArgs args = ConnectDeliveryDetailsFragmentArgs.fromBundle(getArguments());
        boolean isButtonVisible = args.getIsButtonVisible();
        getActivity().setTitle(getString(R.string.connect_job_info_title));

        View view = inflater.inflate(R.layout.fragment_connect_delivery_details, container, false);

        ConnectRegularTextView textView = view.findViewById(R.id.connect_delivery_total_visits_text);
        int maxPossibleVisits = job.getMaxPossibleVisits();
        int daysRemaining = job.getDaysRemaining();
        textView.setText(getString(R.string.connect_job_info_visit, maxPossibleVisits));

        textView = view.findViewById(R.id.connect_delivery_days_text);
        textView.setText(getString(R.string.connect_job_info_days, daysRemaining));

        textView = view.findViewById(R.id.connect_delivery_max_daily_text);
        textView.setText(getString(R.string.connect_job_info_max_visit, job.getMaxDailyVisits()));

        textView = view.findViewById(R.id.connect_delivery_budget_text);
        String paymentText = "";
        if (job.isMultiPayment()) {
            //List payment units
            paymentText = getString(R.string.connect_delivery_earn_multi);
            for (int i = 0; i < job.getPaymentUnits().size(); i++) {
                ConnectPaymentUnitRecord unit = job.getPaymentUnits().get(i);
                paymentText = String.format("%s\n\u2022 %s: %s", paymentText, unit.getName(),
                        job.getMoneyString(unit.getAmount()));
            }
        } else if (job.getPaymentUnits().size() > 0) {
            //Single payment unit
            String moneyValue = job.getMoneyString(job.getPaymentUnits().get(0).getAmount());
            paymentText = getString(R.string.connect_job_info_visit_charge, moneyValue);
        }

        textView.setText(paymentText);

//        boolean expired = daysRemaining < 0;
//        textView = view.findViewById(R.id.connect_delivery_action_title);
//        textView.setText(expired ? R.string.connect_delivery_expired : R.string.connect_delivery_ready_to_claim);

//        textView = view.findViewById(R.id.connect_delivery_action_details);
//        textView.setText(expired ? R.string.connect_delivery_expired_detailed :
//                R.string.connect_delivery_ready_to_claim_detailed);

        boolean jobClaimed = job.getStatus() == ConnectJobRecord.STATUS_DELIVERING;
        boolean installed = false;
        for (ApplicationRecord app : MultipleAppsUtil.appRecordArray()) {
            if (job.getDeliveryAppInfo().getAppId().equals(app.getUniqueId())) {
                installed = true;
                break;
            }
        }
        final boolean appInstalled = installed;

        int buttonTextId = jobClaimed ? (appInstalled ? R.string.connect_delivery_go : R.string.connect_job_info_download) : R.string.connect_job_info_download;

        CardView cardButtonLayout = view.findViewById(R.id.cardButtonLayout);
        cardButtonLayout.setVisibility(isButtonVisible ? View.VISIBLE : View.GONE);

        RoundedButton button = view.findViewById(R.id.connect_delivery_button);
        button.setText(buttonTextId);
//        button.setEnabled(!expired);
        button.setOnClickListener(v -> {
            if (jobClaimed) {
                proceedAfterJobClaimed(button, job, appInstalled);
            } else {
                //Claim job
                ApiConnect.claimJob(getContext(), job.getJobId(), new IApiCallback() {
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
                        ConnectNetworkHelper.showNetworkError(getContext());
                        reportApiCall(false);
                    }

                    @Override
                    public void processOldApiError() {
                        ConnectNetworkHelper.showOutdatedApiError(getContext());
                        reportApiCall(false);
                    }
                });
            }
        });

//        jobCardDataHandle(view, job);
        return view;
    }

    private void jobCardDataHandle(View view, ConnectJobRecord job) {
        View viewJobCard = view.findViewById(R.id.viewJobCard);
        ConnectMediumTextView viewMore = viewJobCard.findViewById(R.id.tv_view_more);
        ConnectBoldTextView tvJobTitle = viewJobCard.findViewById(R.id.tv_job_title);
        ConnectBoldTextView tv_job_time = viewJobCard.findViewById(R.id.tv_job_time);
        ConnectMediumTextView tvJobDiscrepation = viewJobCard.findViewById(R.id.tv_job_discrepation);
        ConnectMediumTextView connect_job_pay = viewJobCard.findViewById(R.id.connect_job_pay);
        ConnectRegularTextView connectJobEndDate = viewJobCard.findViewById(R.id.connect_job_end_date);

        viewMore.setOnClickListener(view1 -> {
            Navigation.findNavController(viewMore).navigate(ConnectDeliveryProgressFragmentDirections.actionConnectJobDeliveryProgressFragmentToConnectJobDeliveryDetailsFragment(false));
        });

        tvJobTitle.setText(job.getTitle());
        tvJobDiscrepation.setText(job.getDescription());
        connect_job_pay.setText(getString(R.string.connect_job_tile_price, job.getMoneyString(job.getBudgetPerVisit())));
        connectJobEndDate.setText(getString(R.string.connect_learn_complete_by, ConnectManager.formatDate(job.getProjectEndDate())));
    }

    private void reportApiCall(boolean success) {
        FirebaseAnalyticsUtil.reportCccApiClaimJob(success);
    }

    private void proceedAfterJobClaimed(Button button, ConnectJobRecord job, boolean installed) {
        job.setStatus(ConnectJobRecord.STATUS_DELIVERING);
        ConnectDatabaseHelper.upsertJob(getContext(), job);

        NavDirections directions;
        if (installed) {
            directions = ConnectDeliveryDetailsFragmentDirections
                    .actionConnectJobDeliveryDetailsFragmentToConnectJobDeliveryProgressFragment();
        } else {
            String title = getString(R.string.connect_downloading_delivery);
            directions = ConnectDeliveryDetailsFragmentDirections
                    .actionConnectJobDeliveryDetailsFragmentToConnectDownloadingFragment(title, false);
        }

        Navigation.findNavController(button).navigate(directions);
    }
}
