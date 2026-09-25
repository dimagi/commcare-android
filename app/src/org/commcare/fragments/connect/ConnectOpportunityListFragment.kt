package org.commcare.fragments.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import org.commcare.AppUtils
import org.commcare.CommCareApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.adapters.ConnectOpportunityListAdapter
import org.commcare.android.database.connect.models.ConnectAppRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE
import org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE_NEW
import org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_DELIVERING
import org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_LEARNING
import org.commcare.connect.ConnectConstants.DELIVERY_APP
import org.commcare.connect.ConnectConstants.LEARN_APP
import org.commcare.connect.ConnectConstants.NEW_APP
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.viewmodel.ConnectJobsListViewModel
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectJobsListBinding
import org.commcare.fragments.RefreshableFragment
import org.commcare.fragments.base.BaseConnectFragment
import org.commcare.models.connect.ConnectLoginJobListModel
import org.commcare.models.connect.ConnectLoginJobListModel.JobListEntryType
import java.util.Date

/** Lists the user's opportunities, grouped into in-progress, new, and completed/expired sections. */
class ConnectOpportunityListFragment :
    BaseConnectFragment<FragmentConnectJobsListBinding>(),
    RefreshableFragment {
    private lateinit var viewModel: ConnectJobsListViewModel

    private var inProgressJobs: List<ConnectLoginJobListModel> = emptyList()
    private var newJobs: List<ConnectLoginJobListModel> = emptyList()
    private var finishedJobs: List<ConnectLoginJobListModel> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        requireActivity().setTitle(R.string.connect_title)
        setWaitDialogEnabled(false)
        viewModel = ViewModelProvider(this)[ConnectJobsListViewModel::class.java]
        observeOpportunities()
        refresh(false)
        return view
    }

    override fun refresh(forceRefresh: Boolean) {
        viewModel.loadOpportunities(forceRefresh)
    }

    private fun observeOpportunities() {
        observeDataState(
            viewModel.opportunities,
            { jobs -> setJobListData(jobs) },
            { jobs -> setJobListData(jobs) },
        )
    }

    private fun initRecyclerView() {
        val noJobsAvailable =
            inProgressJobs.isEmpty() && newJobs.isEmpty() && finishedJobs.isEmpty()
        binding.connectNoJobsText.visibility = if (noJobsAvailable) View.VISIBLE else View.GONE

        val adapter =
            ConnectOpportunityListAdapter(
                inProgressJobs,
                newJobs,
                finishedJobs,
            ) { model ->
                // protecting against double tap
                if (!hasLeftJobsList()) {
                    if (model.isNew) {
                        setActiveJob(model.job)
                        navigateToJobIntro()
                    } else {
                        launchAppForJob(model.job, model.isLearningApp)
                    }
                }
            }

        binding.rvJobList.layoutManager = LinearLayoutManager(context)
        binding.rvJobList.isNestedScrollingEnabled = true
        binding.rvJobList.adapter = adapter
    }

    /** Whether a previous tap has already navigated away, so a second one must be ignored. */
    private fun hasLeftJobsList(): Boolean {
        val destination = binding.root.findNavController().currentDestination
        return destination == null || destination.id != R.id.connect_jobs_list_fragment
    }

    private fun navigateToJobIntro() {
        binding.root.findNavController().navigate(
            ConnectOpportunityListFragmentDirections
                .actionConnectJobsListFragmentToConnectJobIntroFragment(),
        )
    }

    private fun launchAppForJob(
        job: ConnectJobRecord,
        isLearning: Boolean,
    ) {
        setActiveJob(job)

        if (isLearning) {
            navigateToLearnProgress()
        } else {
            navigateToDeliveryProgress()
        }
    }

    private fun navigateToDeliveryProgress() {
        binding.root.findNavController().navigate(
            ConnectOpportunityListFragmentDirections
                .actionConnectJobsListFragmentToConnectJobDeliveryProgressFragment(),
        )
    }

    private fun navigateToLearnProgress() {
        binding.root.findNavController().navigate(
            ConnectOpportunityListFragmentDirections
                .actionConnectJobsListFragmentToConnectJobLearningProgressFragment(),
        )
    }

    private fun setActiveJob(job: ConnectJobRecord) {
        CommCareApplication.instance().setConnectJobIdForAnalytics(job)
        (requireActivity() as ConnectActivity).activeJob = job
    }

    private fun setJobListData(jobs: List<ConnectJobRecord>) {
        val inProgress = mutableListOf<ConnectLoginJobListModel>()
        val inProgressComplete = mutableListOf<ConnectLoginJobListModel>()
        val new = mutableListOf<ConnectLoginJobListModel>()
        val finished = mutableListOf<ConnectLoginJobListModel>()

        for (job in jobs) {
            val isLearnAppInstalled = AppUtils.isAppInstalled(job.learnAppInfo.appId)
            val isDeliverAppInstalled = AppUtils.isAppInstalled(job.deliveryAppInfo.appId)
            val compositeJob = requireNotNull(ConnectJobUtils.getCompositeJob(job.jobUUID))
            val userCompletedDelivery =
                compositeJob.status == STATUS_DELIVERING &&
                    compositeJob.getDeliveryProgressPercentage() == 100

            if (compositeJob.isFinished) {
                finished.add(
                    createJobModel(
                        compositeJob,
                        JobListEntryType.DELIVERY,
                        DELIVERY_APP,
                        isDeliverAppInstalled,
                        isNew = false,
                        isLearningApp = false,
                        isDeliveryApp = true,
                        jobFinished = true,
                        userCompletedDelivery = userCompletedDelivery,
                    ),
                )
                continue
            }

            when (job.status) {
                STATUS_AVAILABLE_NEW, STATUS_AVAILABLE -> {
                    new.add(
                        createJobModel(
                            compositeJob,
                            JobListEntryType.NEW_OPPORTUNITY,
                            NEW_APP,
                            isAppInstalled = true,
                            isNew = true,
                            isLearningApp = false,
                            isDeliveryApp = false,
                            jobFinished = false,
                            userCompletedDelivery = false,
                        ),
                    )
                }

                STATUS_LEARNING -> {
                    inProgress.add(
                        createJobModel(
                            compositeJob,
                            JobListEntryType.LEARNING,
                            LEARN_APP,
                            isLearnAppInstalled,
                            isNew = false,
                            isLearningApp = true,
                            isDeliveryApp = false,
                            jobFinished = false,
                            userCompletedDelivery = false,
                        ),
                    )
                }

                STATUS_DELIVERING -> {
                    val model =
                        createJobModel(
                            compositeJob,
                            JobListEntryType.DELIVERY,
                            DELIVERY_APP,
                            isDeliverAppInstalled,
                            isNew = false,
                            isLearningApp = false,
                            isDeliveryApp = true,
                            jobFinished = false,
                            userCompletedDelivery = userCompletedDelivery,
                        )

                    if (userCompletedDelivery) {
                        inProgressComplete.add(model)
                    } else {
                        inProgress.add(model)
                    }
                }
            }
        }

        inProgress.sortBy { it.lastAccessed }
        inProgressComplete.sortBy { it.lastAccessed }

        // Jobs with completed delivery moved to the end
        inProgress.addAll(inProgressComplete)

        new.sortBy { it.lastAccessed }
        finished.sortBy { it.lastAccessed }

        inProgressJobs = inProgress
        newJobs = new
        finishedJobs = finished
        initRecyclerView()
    }

    private fun createJobModel(
        job: ConnectJobRecord,
        jobType: JobListEntryType,
        appType: String,
        isAppInstalled: Boolean,
        isNew: Boolean,
        isLearningApp: Boolean,
        isDeliveryApp: Boolean,
        jobFinished: Boolean,
        userCompletedDelivery: Boolean,
    ): ConnectLoginJobListModel =
        ConnectLoginJobListModel(
            job.title,
            job.jobUUID,
            getAppRecord(job, jobType).appId,
            job.projectEndDate,
            getAppRecord(job, jobType).description,
            getAppRecord(job, jobType).organization,
            isAppInstalled,
            isNew,
            isLearningApp,
            isDeliveryApp,
            processJobRecords(job, jobType),
            job.getLearningPercentComplete(true),
            job.getDeliveryProgressPercentage(),
            jobType,
            appType,
            job,
            jobFinished,
            userCompletedDelivery,
        )

    private fun getAppRecord(
        job: ConnectJobRecord,
        jobType: JobListEntryType,
    ): ConnectAppRecord = if (jobType == JobListEntryType.LEARNING) job.learnAppInfo else job.deliveryAppInfo

    fun processJobRecords(
        job: ConnectJobRecord,
        jobType: JobListEntryType,
    ): Date {
        val user = ConnectUserDatabaseUtil.getUser()
        val appId = getAppRecord(job, jobType).appId
        val appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(appId, user.userId)
        return appRecord?.lastAccessed ?: Date()
    }

    override fun getEndpoint(): String = ConnectRepository.SYNC_KEY_OPPORTUNITIES

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentConnectJobsListBinding = FragmentConnectJobsListBinding.inflate(inflater, container, false)
}
