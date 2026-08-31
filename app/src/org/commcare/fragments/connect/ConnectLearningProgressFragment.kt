package org.commcare.fragments.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDirections
import androidx.navigation.Navigation
import org.commcare.AppUtils
import org.commcare.connect.ConnectAppLaunchController
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.ConnectLearningProgressViewModel
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectLearningProgressBinding
import org.commcare.fragments.RefreshableFragment
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

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

    private fun observeLearningProgress() {
        observeDataState(
            viewModel.learningProgress,
            { cached ->
                job = cached
                updateLearningUI()
            },
            { success ->
                job = success
                updateLearningUI()
            },
        )
    }

    private fun updateLearningUI() {
        val learnCompletionDate = job.latestLearningActivityDate
        val showLearningComplete =
            learnCompletionDate != null && job.getLearningPercentComplete(false) >= 100 && job.passedAssessment()

        binding.learnProgressView.visibility = if (showLearningComplete) View.GONE else View.VISIBLE
        binding.learnCompleteView.visibility = if (showLearningComplete) View.VISIBLE else View.GONE

        if (showLearningComplete) {
            binding.learnCompleteView.bind(
                job,
                learnCompletionDate,
                ConnectUserDatabaseUtil.getUser(requireContext()).name,
                View.OnClickListener { onDeliveryCtaClicked() },
            )
        } else {
            binding.learnProgressView.bind(job, View.OnClickListener { navigateToLearnAppHome() })
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
                        val deliveryAppInstalled = AppUtils.isAppInstalled(job.deliveryAppInfo.appId)
                        Navigation.findNavController(requireView()).navigate(
                            if (deliveryAppInstalled) navigateToDeliveryProgress() else navigateToDeliveryDownload(),
                        )
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

    private fun navigateToDeliveryProgress(): NavDirections =
        ConnectLearningProgressFragmentDirections
            .actionConnectJobLearningProgressFragmentToConnectJobDeliveryProgressFragment()

    private fun navigateToDeliveryDownload(): NavDirections =
        ConnectLearningProgressFragmentDirections
            .actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(
                getString(R.string.connect_downloading_delivery),
                false,
            )

    private fun navigateToLearnAppHome() {
        val appId = job.learnAppInfo.appId

        if (AppUtils.isAppInstalled(appId)) {
            ConnectAppLaunchController(this).launchApp(appId, true, Runnable { popSelfOnceHidden() })
        } else {
            Navigation.findNavController(binding.root).navigate(
                ConnectLearningProgressFragmentDirections
                    .actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(
                        getString(R.string.connect_downloading_learn),
                        true,
                    ),
            )
        }
    }

    override fun getEndpoint(): String = ConnectRepository.SYNC_KEY_LEARNING_PREFIX + job.jobUUID

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentConnectLearningProgressBinding = FragmentConnectLearningProgressBinding.inflate(inflater, container, false)
}
