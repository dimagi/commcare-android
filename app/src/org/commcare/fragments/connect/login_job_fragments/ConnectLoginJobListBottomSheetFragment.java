package org.commcare.fragments.connect.login_job_fragments;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayoutMediator;

import org.commcare.adapters.JobListCombinedAdapter;
import org.commcare.adapters.JobListViewPagerAdapter;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.BottomsheetLoginJoblistBinding;
import org.commcare.testingModel.CommCareItem;
import org.commcare.testingModel.ConnectHomeItem;

import java.util.ArrayList;
import java.util.List;

public class ConnectLoginJobListBottomSheetFragment extends BottomSheetDialogFragment {

    private BottomsheetLoginJoblistBinding binding;

    public static ConnectLoginJobListBottomSheetFragment newInstance() {
        return new ConnectLoginJobListBottomSheetFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomsheetLoginJoblistBinding.inflate(inflater, container, false);
        binding.rootView.setBackgroundResource(R.drawable.connect_bottom_sheet_rounded_corners);

        handleViewVisibility();
        if (isAppsSeparated()) {
            setupViewPager();
        } else {
            setUpRecyclerView();
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        customizeBottomSheetBackground(view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupViewPager() {
        JobListViewPagerAdapter viewPagerAdapter = new JobListViewPagerAdapter(getActivity());
        viewPagerAdapter.add(new ConnectLoginConnectHomeAppsFragment(), "Connect Home");
        viewPagerAdapter.add(new ConnectLoginCommcareAppsFragment(), "CommCare App");

        binding.viewPager.setAdapter(viewPagerAdapter);
        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(viewPagerAdapter.getPageTitle(position))
        ).attach();
    }

    private void setUpRecyclerView() {
        // Initialize data
        List<Object> items = new ArrayList<>();
        items.add(new CommCareItem("CommCare App 1", "Description for CommCare App 1"));
        items.add(new ConnectHomeItem("Connect Home 1", "Address for Connect Home 1"));
        items.add(new CommCareItem("CommCare App 2", "Description for CommCare App 2"));
        items.add(new ConnectHomeItem("Connect Home 2", "Address for Connect Home 2"));
        items.add(new ConnectHomeItem("Connect Home 2", "Address for Connect Home 2"));
        items.add(new ConnectHomeItem("Connect Home 2", "Address for Connect Home 2"));
        items.add(new CommCareItem("CommCare App 2", "Description for CommCare App 2"));
        items.add(new ConnectHomeItem("Connect Home 2", "Address for Connect Home 2"));
        items.add(new CommCareItem("CommCare App 2", "Description for CommCare App 2"));

        JobListCombinedAdapter adapter = new JobListCombinedAdapter(getContext(), items);
        binding.rvJobListApps.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvJobListApps.setNestedScrollingEnabled(true);
        binding.rvJobListApps.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                rv.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {

            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

            }
        });

        binding.rvJobListApps.setAdapter(adapter);
    }

    private boolean isAppsSeparated() {
        return false;
    }

    private void handleViewVisibility() {
        if (isAppsSeparated()) {
            binding.llJobListTabSeparated.setVisibility(View.VISIBLE);
            binding.rvJobListApps.setVisibility(View.GONE);
        } else {
            binding.llJobListTabSeparated.setVisibility(View.GONE);
            binding.rvJobListApps.setVisibility(View.VISIBLE);
        }
    }

    private void customizeBottomSheetBackground(View view) {
        view.getViewTreeObserver().addOnPreDrawListener(() -> {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            if (dialog != null) {
                View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    bottomSheet.setBackground(new ColorDrawable(
                            ContextCompat.getColor(requireContext(), R.color.transparent)
                    ));
                }
            }
            return true;
        });
    }
}
