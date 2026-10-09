package org.commcare.fragments.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import org.commcare.AppUtils
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.opportunity.OpportunityNavigator
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.AppInstallState
import org.commcare.connect.viewmodel.ConnectLearningProgressViewModel
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectLearningProgressBinding
import org.commcare.fragments.RefreshableFragment
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import java.util.Date

class ConnectLearningProgressFragment :
    ConnectJobFragment<FragmentConnectLearningProgressBinding>(),
    RefreshableFragment {
    private lateinit var viewModel: ConnectLearningProgressViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        requireActivity().setTitle(R.string.connect_learn_title)
        setWaitDialogEnabled(false)
        viewModel =
            ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application),
            )[ConnectLearningProgressViewModel::class.java]

        updateLearningUI()
        observeLearningProgress()
        observeClaimJob()
        return view
    }

    override fun onResume() {
        super.onResume()
        if (PersonalIdManager.getInstance().isloggedIn()) {
            refresh(false)
        }
    }

    override fun refresh(forceRefresh: Boolean) {
        viewModel.loadLearningProgress(job, forceRefresh)
    }

    override fun informSyncStatus(
        lastSyncStatus: CharSequence,
        synced: Boolean,
    ) {
        binding.learnProgressView.updateSyncStatus(lastSyncStatus, synced)
    }

    /** Publishes the refreshed job to the host so Opportunity Home resolves against current data. */
    private fun observeLearningProgress() {
        observeDataState(
            viewModel.learningProgress,
            { cached -> publishRefreshedJob(cached) },
            { success -> publishRefreshedJob(success) },
        )
    }

    /** A refresh begun before the claim landed carries the pre-claim status and must not replace the claimed job. */
    private fun publishRefreshedJob(refreshed: ConnectJobRecord) {
        val claimed = viewModel.claimJob.value is DataState.Success
        if (claimed && refreshed.status != ConnectJobRecord.STATUS_DELIVERING) {
            return
        }
        setActiveJob(refreshed)
        updateLearningUI()
    }

    /**
     * The job shown may be a copy a refresh published while the claim was in flight, so the stored
     * job is published as delivering before the page resolves its surface.
     */
    private fun publishClaimedJob() {
        val claimed = ConnectJobUtils.getCompositeJob(job.jobUUID) ?: job
        claimed.status = ConnectJobRecord.STATUS_DELIVERING
        setActiveJob(claimed)
    }

    /** Which of this screen's two views is showing: the completion summary, or learning still in progress. */
    private val showLearningComplete
        get() = job.getLearningPercentComplete(false) >= 100 && job.passedAssessment()

    private fun updateLearningUI() {
        binding.learnProgressView.visibility = if (showLearningComplete) View.GONE else View.VISIBLE
        binding.learnCompleteView.visibility = if (showLearningComplete) View.VISIBLE else View.GONE

        if (showLearningComplete) {
            binding.learnCompleteView.bind(
                job,
                job.latestLearningActivityDate ?: Date(),
                ConnectUserDatabaseUtil.getUser().name,
                { onDeliveryCtaClicked() },
            )
        } else {
            binding.learnProgressView.bind(
                job,
                { launchApp(isLearning = true) },
                { refresh(true) },
            )
        }
    }

    private fun onDeliveryCtaClicked() {
        binding.learnCompleteView.hideClaimFailure()
        binding.learnCompleteView.isCtaEnabled = false
        viewModel.claimJob(job)
    }

    private fun observeClaimJob() {
        viewModel.claimJob.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DataState.Success -> {
                    FirebaseAnalyticsUtil.reportCccApiClaimJob(true)
                    if (hasLiveView()) {
                        publishClaimedJob()
                        if (AppUtils.isAppInstalled(job.deliveryAppInfo.appId)) {
                            OpportunityNavigator
                                .hostOf(this@ConnectLearningProgressFragment)
                                ?.onPhaseChanged()
                        } else {
                            launchApp(isLearning = false)
                        }
                    }
                }

                is DataState.Error -> {
                    FirebaseAnalyticsUtil.reportCccApiClaimJob(false)
                    if (hasLiveView()) {
                        binding.learnCompleteView.isCtaEnabled = true
                        binding.learnCompleteView.showClaimFailure(claimFailureMessage(state))
                    }
                }

                else -> {
                    Unit
                }
            }
        }
    }

    private fun claimFailureMessage(error: DataState.Error<Unit>): String =
        if (error.errorCode == PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR) {
            getString(R.string.recovery_unable_to_claim_opportunity)
        } else {
            PersonalIdOrConnectApiErrorHandler.handle(requireContext(), error.errorCode, error.throwable)
        }

    override fun onAppInstallStateChanged(
        state: AppInstallState,
        isLearning: Boolean,
    ) {
        if (showLearningComplete) {
            binding.learnCompleteView.renderAppInstallState(state, isLearning, ::forgetInstallFailure)
        } else {
            binding.learnProgressView.renderAppInstallState(state, isLearning, ::forgetInstallFailure)
        }
    }

    override fun getEndpoint(): String = ConnectRepository.SYNC_KEY_LEARNING_PREFIX + job.jobUUID

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentConnectLearningProgressBinding = FragmentConnectLearningProgressBinding.inflate(inflater, container, false)
}
