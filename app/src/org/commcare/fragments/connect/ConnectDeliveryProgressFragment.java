package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;

import org.commcare.activities.connect.ConnectDatabaseHelper;
import org.commcare.activities.connect.ConnectManager;
import org.commcare.activities.connect.ConnectNetworkHelper;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Fragment for showing delivery progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectDeliveryProgressFragment extends Fragment {
    private ConnectJobRecord job;
    private ConnectDeliveryProgressFragment.ViewStateAdapter viewStateAdapter;
    private TextView updateText;

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
        job = ConnectDeliveryProgressFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_delivery_progress, container, false);

        updateText = view.findViewById(R.id.connect_delivery_last_update);
        updateUpdatedDate(job.getLastDeliveryUpdate());

        ImageView refreshButton = view.findViewById(R.id.connect_delivery_refresh);
        refreshButton.setOnClickListener(v -> {
            refreshData();
        });

        final ViewPager2 pager = view.findViewById(R.id.connect_delivery_progress_view_pager);
        viewStateAdapter = new ConnectDeliveryProgressFragment.ViewStateAdapter(getChildFragmentManager(), getLifecycle(), job);
        pager.setAdapter(viewStateAdapter);

        final TabLayout tabLayout = view.findViewById(R.id.connect_delivery_progress_tabs);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_progress_delivery));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_progress_delivery_verification));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                TabLayout.Tab tab = tabLayout.getTabAt(position);
                tabLayout.selectTab(tab);

                FirebaseAnalyticsUtil.reportConnectTabChange(tab.getText().toString());
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                pager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if(ConnectManager.isUnlocked()) {
            refreshData();
        }
    }

    public void refreshData() {
        ConnectNetworkHelper.getDeliveries(getContext(), job.getJobId(), new ConnectNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONObject json = new JSONObject(responseAsString);

                        boolean updatedJob = false;
                        String key = "max_payments";
                        if(json.has(key)) {
                            job.setMaxVisits(json.getInt(key));
                            updatedJob = true;
                        }

                        key = "end_date";
                        if(json.has(key)) {
                            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            job.setProjectEndDate(df.parse(json.getString(key)));
                            updatedJob = true;
                        }

                        key = "payment_accrued";
                        if(json.has(key)) {
                            job.setPaymentAccrued(json.getInt(key));
                            updatedJob = true;
                        }

                        if(updatedJob) {
                            job.setLastDeliveryUpdate(new Date());
                            ConnectDatabaseHelper.upsertJob(getContext(), job);
                        }

                        List<ConnectJobDeliveryRecord> deliveries = new ArrayList<>(json.length());
                        key = "deliveries";
                        if(json.has(key)) {
                            JSONArray array = json.getJSONArray(key);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = (JSONObject)array.get(i);
                                deliveries.add(ConnectJobDeliveryRecord.fromJson(obj, job.getJobId()));
                            }

                            //Store retrieved deliveries
                            ConnectDatabaseHelper.storeDeliveries(getContext(), deliveries, job.getJobId(), true);

                            job.setDeliveries(deliveries);
                        }

                        List<ConnectJobPaymentRecord> payments = new ArrayList<>();
                        key = "payments";
                        if(json.has(key)) {
                            JSONArray array = json.getJSONArray(key);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = (JSONObject)array.get(i);
                                payments.add(ConnectJobPaymentRecord.fromJson(obj, job.getJobId()));
                            }

                            ConnectDatabaseHelper.storePayments(getContext(), payments, job.getJobId(), true);

                            job.setPayments(payments);
                        }

                        try {
                            updateUpdatedDate(new Date());
                            viewStateAdapter.refresh();
                        }
                        catch(Exception e) {
                            //Ignore exception, happens if we leave the page before API call finishes
                        }
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from delivery progress request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Delivery progress call failed: %d", responseCode));
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
            }
        });
    }

    private void updateUpdatedDate(Date lastUpdate) {
        DateFormat df = SimpleDateFormat.getDateTimeInstance();
        updateText.setText(getString(R.string.connect_last_update, df.format(lastUpdate)));
    }

    private static class ViewStateAdapter extends FragmentStateAdapter {
        private final ConnectJobRecord job;
        private static ConnectDeliveryProgressDeliveryFragment deliveryFragment = null;
        private static ConnectResultsSummaryListFragment verificationFragment = null;
        public ViewStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle, ConnectJobRecord job) {
            super(fragmentManager, lifecycle);
            this.job = job;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                deliveryFragment = ConnectDeliveryProgressDeliveryFragment.newInstance(job);
                return deliveryFragment;
            }

            verificationFragment = ConnectResultsSummaryListFragment.newInstance(job);
            return verificationFragment;
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        public void refresh() {
            if(deliveryFragment != null) {
                deliveryFragment.updateView();
            }

            if(verificationFragment != null) {
                verificationFragment.updateView();
            }
        }
    }
}
