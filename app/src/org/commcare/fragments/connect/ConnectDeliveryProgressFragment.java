package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.tabs.TabLayout;

import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.dalvik.R;

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
        ConnectJob job = ConnectDeliveryProgressFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_delivery_progress, container, false);

        final ViewPager2 pager = view.findViewById(R.id.connect_delivery_progress_view_pager);
        pager.setAdapter(new ConnectDeliveryProgressFragment.ViewStateAdapter(getChildFragmentManager(), getLifecycle(), job));

        final TabLayout tabLayout = view.findViewById(R.id.connect_delivery_progress_tabs);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_progress_delivery));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_progress_delivery_verification));

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
        private ConnectJob job;
        public ViewStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle, ConnectJob job) {
            super(fragmentManager, lifecycle);
            this.job = job;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return ConnectDeliveryProgressDeliveryFragment.newInstance(job);
            }

            return ConnectDeliveryProgressVerificationListFragment.newInstance(job);
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
