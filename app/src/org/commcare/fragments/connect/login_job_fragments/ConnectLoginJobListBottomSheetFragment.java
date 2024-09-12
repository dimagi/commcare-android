package org.commcare.fragments.connect.login_job_fragments;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayoutMediator;

import org.commcare.adapters.JobListCombinedAdapter;
import org.commcare.adapters.JobListViewPagerAdapter;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.BottomsheetLoginJoblistBinding;
import org.commcare.interfaces.JobListCallBack;
import org.commcare.models.connect.ConnectCombineJobListModel;
import org.commcare.models.connect.ConnectLoginJobListModel;

import java.util.ArrayList;
import java.util.List;

public class ConnectLoginJobListBottomSheetFragment extends BottomSheetDialogFragment {

    private BottomsheetLoginJoblistBinding binding;
    private static final String ARG_JOB_LIST = "job_list";
    private static final String ARG_TRADITIONAL_JOB_LIST = "traditional_job_list";
    private List<ConnectLoginJobListModel> jobList;
    private List<ConnectLoginJobListModel> traditionalJobList;
    private JobListCallBack mCallback;


    public static ConnectLoginJobListBottomSheetFragment newInstance(List<ConnectLoginJobListModel> jobList, List<ConnectLoginJobListModel> traditionalJobList,JobListCallBack mCallback) {
        ConnectLoginJobListBottomSheetFragment fragment = new ConnectLoginJobListBottomSheetFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_JOB_LIST, new ArrayList<>(jobList));
        args.putParcelableArrayList(ARG_TRADITIONAL_JOB_LIST, new ArrayList<>(traditionalJobList));
        fragment.setArguments(args);
        fragment.setOnJobListClickedListener(mCallback);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomsheetLoginJoblistBinding.inflate(inflater, container, false);
        binding.rootView.setBackgroundResource(R.drawable.connect_bottom_sheet_rounded_corners);

        retrieveArguments();
        setupUI();

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

    private void retrieveArguments() {
        if (getArguments() != null) {
            jobList = getArguments().getParcelableArrayList(ARG_JOB_LIST);
            traditionalJobList = getArguments().getParcelableArrayList(ARG_TRADITIONAL_JOB_LIST);
        }
    }

    private void setupUI() {
        if (isAppsSeparated()) {
            setupViewPager();
        } else {
            setupCombinedRecyclerView();
        }
    }

    /**
     * Setup ViewPager with tabs for separated job lists (Connect Home and CommCare App).
     */
    private void setupViewPager() {
        JobListViewPagerAdapter viewPagerAdapter = new JobListViewPagerAdapter(requireActivity());
        viewPagerAdapter.add(ConnectLoginConnectHomeAppsFragment.newInstance(jobList, (appId,jobName,jobType) -> mCallback.onClick(appId,jobName,jobType)), "Connect Home");
        viewPagerAdapter.add(ConnectLoginCommcareAppsFragment.newInstance(traditionalJobList, (appId,jobName,jobType) -> mCallback.onClick(appId,jobName,jobType)), "CommCare App");

        configureRecyclerViewScrolling();

        binding.viewPager.setAdapter(viewPagerAdapter);
        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(viewPagerAdapter.getPageTitle(position))
        ).attach();
    }

    /**
     * Setup RecyclerView with combined job lists if apps are not separated.
     */
    private void setupCombinedRecyclerView() {
        JobListViewPagerAdapter viewPagerAdapter = new JobListViewPagerAdapter(requireActivity());
        viewPagerAdapter.add(ConnectLoginCombineAppsFragment.newInstance(combineJobLists(), (appId,jobName,jobType) -> mCallback.onClick(appId,jobName,jobType)), "Connect Home");

        configureRecyclerViewScrolling();

        binding.viewPager.setAdapter(viewPagerAdapter);
        handlePagerUI();
    }

    private void handlePagerUI() {
        binding.tabLayout.setVisibility(View.GONE);
        binding.divider.setVisibility(View.GONE);
    }

    /**
     * Combine Connect Home and CommCare job lists into a single list.
     *
     * @return Combined list of jobs.
     */
    private List<ConnectCombineJobListModel> combineJobLists() {
        List<ConnectCombineJobListModel> combineAppsList = new ArrayList<>();
        combineAppsList.addAll(createCombinedList(jobList, JobListCombinedAdapter.VIEW_TYPE_CONNECT_HOME));
        combineAppsList.addAll(createCombinedList(traditionalJobList, JobListCombinedAdapter.VIEW_TYPE_COMMCARE));
        return combineAppsList;
    }

    /**
     * Create a combined list of jobs with the specified view type.
     *
     * @param jobList  The list of jobs to combine.
     * @param listType The type of list (Connect Home or CommCare).
     * @return A combined list of jobs with the specified view type.
     */
    private List<ConnectCombineJobListModel> createCombinedList(List<ConnectLoginJobListModel> jobList, int listType) {
        List<ConnectCombineJobListModel> combinedList = new ArrayList<>();
        for (ConnectLoginJobListModel job : jobList) {
            combinedList.add(new ConnectCombineJobListModel(job, listType));
        }
        return combinedList;
    }

    private boolean isAppsSeparated() {
        return jobList.size() > 2 && traditionalJobList.size() > 2;
    }

    private void configureRecyclerViewScrolling() {
        for (int i = 0; i < binding.viewPager.getChildCount(); i++) {
            View child = binding.viewPager.getChildAt(i);
            if (child instanceof RecyclerView) {
                child.setNestedScrollingEnabled(false);
                break;
            }
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

    public void setOnJobListClickedListener(JobListCallBack mCallback) {
        this.mCallback = mCallback;
    }
}
