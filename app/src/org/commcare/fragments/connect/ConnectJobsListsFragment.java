package org.commcare.fragments.connect;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;

import org.commcare.activities.connect.ConnectDatabaseHelper;
import org.commcare.activities.connect.ConnectManager;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.CommCareApplication;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for showing the two job lists (available and mine)
 *
 * @author dviggiano
 */
public class ConnectJobsListsFragment extends Fragment {
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ViewStateAdapter viewStateAdapter;
    private TextView updateText;

    public ConnectJobsListsFragment() {
        // Required empty public constructor
    }

    public static ConnectJobsListsFragment newInstance() {
        return new ConnectJobsListsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getActivity().setTitle(R.string.connect_title);

        ConnectManager.setActiveJob(null);

        View view = inflater.inflate(R.layout.fragment_connect_jobs_list, container, false);

        updateText = view.findViewById(R.id.connect_jobs_last_update);
        updateUpdatedDate(ConnectDatabaseHelper.getLastJobsUpdate(getContext()));

        ImageView refreshButton = view.findViewById(R.id.connect_jobs_refresh);
        refreshButton.setOnClickListener(v -> refreshData());

        viewPager = view.findViewById(R.id.jobs_view_pager);
        viewStateAdapter = new ViewStateAdapter(getChildFragmentManager(), getLifecycle());
        viewPager.setAdapter(viewStateAdapter);

        tabLayout = view.findViewById(R.id.connect_jobs_tabs);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_jobs_all));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_jobs_mine));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
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
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        chooseTab();
        refreshData();

        return view;
    }

    public void refreshData() {
        ApiConnect.getConnectOpportunities(getContext(), new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                int newJobs = 0;
                //TODO: Sounds like we don't want a try-catch here, better to crash. Verify before changing
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONArray json = new JSONArray(responseAsString);
                        List<ConnectJobRecord> jobs = new ArrayList<>(json.length());
                        for (int i = 0; i < json.length(); i++) {
                            JSONObject obj = (JSONObject)json.get(i);
                            jobs.add(ConnectJobRecord.fromJson(obj));
                        }

                        //Store retrieved jobs
                        newJobs = ConnectDatabaseHelper.storeJobs(getContext(), jobs, true);
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from Opportunities request", e);
                }

                reportApiCall(true, newJobs);

                refreshUi();
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Opportunities call failed: %d", responseCode));
                reportApiCall(false, 0);
                refreshUi();
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
                reportApiCall(false, 0);
                refreshUi();
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(getContext());
                reportApiCall(false, 0);
            }
        });
    }

    private void reportApiCall(boolean success, int newJobs) {
        FirebaseAnalyticsUtil.reportCccApiJobs(success, newJobs);
    }

    private void refreshUi() {
        try {
            updateUpdatedDate(new Date());
            viewStateAdapter.refresh();
            chooseTab();
        }
        catch(Exception e) {
            //Ignore exception, happens if we leave the page before API call finishes
        }
    }

    private void updateUpdatedDate(Date lastUpdate) {
        updateText.setText(getString(R.string.connect_last_update, ConnectManager.formatDateTime(lastUpdate)));
    }

    private void chooseTab() {
        int numAvailable = ConnectDatabaseHelper.getAvailableJobs(CommCareApplication.instance()).size();
        int index = numAvailable > 0 ? 0 : 1;
        viewPager.setCurrentItem(index);
        tabLayout.setScrollPosition(index, 0f, true);
    }

    private static class ViewStateAdapter extends FragmentStateAdapter {
        static ConnectJobsAvailableListFragment availableFragment;
        static ConnectJobsMyListFragment myFragment;

        public ViewStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
            super(fragmentManager, lifecycle);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                availableFragment = ConnectJobsAvailableListFragment.newInstance();
                return availableFragment;
            }

            myFragment = ConnectJobsMyListFragment.newInstance();
            return myFragment;
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        public void refresh() {
            if (availableFragment != null) {
                availableFragment.updateView();
            }

            if (myFragment != null) {
                myFragment.updateView();
            }
        }
    }
}