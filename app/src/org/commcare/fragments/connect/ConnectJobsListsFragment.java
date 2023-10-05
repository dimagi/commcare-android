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
import org.commcare.activities.connect.ConnectNetworkHelper;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;
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

/**
 * Fragment for showing the two job lists (available and mine)
 *
 * @author dviggiano
 */
public class ConnectJobsListsFragment extends Fragment {
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

        View view = inflater.inflate(R.layout.fragment_connect_jobs_list, container, false);

        updateText = view.findViewById(R.id.connect_jobs_last_update);
        updateUpdatedDate(ConnectDatabaseHelper.getLastJobsUpdate(getContext()));

        ImageView refreshButton = view.findViewById(R.id.connect_jobs_refresh);
        refreshButton.setOnClickListener(v -> {
            refreshData();
        });

        final ViewPager2 pager = view.findViewById(R.id.jobs_view_pager);
        viewStateAdapter = new ViewStateAdapter(getChildFragmentManager(), getLifecycle());
        pager.setAdapter(viewStateAdapter);

        final TabLayout tabLayout = view.findViewById(R.id.connect_jobs_tabs);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_jobs_all));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_jobs_mine));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tabLayout.selectTab(tabLayout.getTabAt(position));
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

        refreshData();

        return view;
    }

    public void refreshData() {
        ConnectNetworkHelper.getConnectOpportunities(getContext(), new ConnectNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
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
                        ConnectDatabaseHelper.storeJobs(getContext(), jobs, true);

                        updateUpdatedDate(new Date());
                        viewStateAdapter.refresh();
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from Opportunities request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Opportunities call failed: %d", responseCode));
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
        ConnectJobsAvailableListFragment availableFragment;
        ConnectJobsMyListFragment myFragment;

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