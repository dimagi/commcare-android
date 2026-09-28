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
                    setActiveJob(model.job)
                    navigateToOpportunityHome()
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

    /** Opens the opportunity's page, which picks the surface from the job's phase. */
    private fun navigateToOpportunityHome() {
        binding.root.findNavController().navigate(
            ConnectOpportunityListFragmentDirections
                .actionConnectJobsListFragmentToOpportunityHomeFragment(),
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
                        isDeliverAppInstalled,
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
                            isAppInstalled = true,
                        ),
                    )
                }

                STATUS_LEARNING -> {
                    inProgress.add(
                        createJobModel(compositeJob, JobListEntryType.LEARNING, isLearnAppInstalled),
                    )
                }

                STATUS_DELIVERING -> {
                    val model =
                        createJobModel(
                            compositeJob,
                            JobListEntryType.DELIVERY,
                            isDeliverAppInstalled,
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

    /** Builds a row for [job] as [jobType]; the app type and row-kind flags are derived from it. */
    private fun createJobModel(
        job: ConnectJobRecord,
        jobType: JobListEntryType,
        isAppInstalled: Boolean,
        jobFinished: Boolean = false,
        userCompletedDelivery: Boolean = false,
    ): ConnectLoginJobListModel {
        val appRecord = getAppRecord(job, jobType)
        return ConnectLoginJobListModel(
            job.title,
            job.jobUUID,
            appRecord.appId,
            job.projectEndDate,
            appRecord.description,
            appRecord.organization,
            isAppInstalled,
            jobType == JobListEntryType.NEW_OPPORTUNITY,
            jobType == JobListEntryType.LEARNING,
            jobType == JobListEntryType.DELIVERY,
            processJobRecords(job, jobType),
            job.getLearningPercentComplete(true),
            job.getDeliveryProgressPercentage(),
            jobType,
            appTypeFor(jobType),
            job,
            jobFinished,
            userCompletedDelivery,
        )
    }

    private fun appTypeFor(jobType: JobListEntryType): String =
        when (jobType) {
            JobListEntryType.LEARNING -> LEARN_APP
            JobListEntryType.DELIVERY -> DELIVERY_APP
            JobListEntryType.NEW_OPPORTUNITY -> NEW_APP
        }

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
