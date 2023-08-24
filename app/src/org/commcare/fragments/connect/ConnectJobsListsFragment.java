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

import com.google.android.material.tabs.TabLayout;

import org.commcare.dalvik.R;

/**
 * Fragment for showing the two job lists (available and mine)
 *
 * @author dviggiano
 */
public class ConnectJobsListsFragment extends Fragment {

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

        final ViewPager2 pager = view.findViewById(R.id.jobs_view_pager);
        pager.setAdapter(new ViewStateAdapter(getChildFragmentManager(), getLifecycle()));

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

        return view;
    }

    private static class ViewStateAdapter extends FragmentStateAdapter {
        public ViewStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
            super(fragmentManager, lifecycle);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return ConnectJobsAvailableListFragment.newInstance();
            }

            return ConnectJobsMyListFragment.newInstance();
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}