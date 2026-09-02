package org.commcare.fragments.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectDateUtils
import org.commcare.connect.ConnectMoneyUtils
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.ConnectJobIntroViewModel
import org.commcare.connect.viewmodel.InstallState
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectJobIntroBinding
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.views.connect.renderInstallState
import java.text.DateFormat

/**
 * Fragment for showing detailed info about an available job
 *
 */
class ConnectJobIntroFragment : ConnectJobFragment<FragmentConnectJobIntroBinding>() {
    private lateinit var viewModel: ConnectJobIntroViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        requireActivity().setTitle(R.string.connect_job_info_view_opportunity)

        (requireActivity() as AppCompatActivity)
            .supportActionBar!!
            .setHomeAsUpIndicator(R.drawable.ic_connect_close)

        viewModel =
            ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application),
            )[ConnectJobIntroViewModel::class.java]

        observeStartLearning()

        binding.tvJobTitle.text = job.title
        binding.tvJobDescription.text = job.shortDescription
        binding.tvExpiryValue.text =
            ConnectDateUtils.formatDate(job.projectEndDate, DateFormat.SHORT)
        binding.tvMaxEarningsValue.text =
            ConnectMoneyUtils.moneyStringWithSymbol(job.currency, job.totalBudget)

        binding.connectIntroCtaBar.setOnCtaClickListener { startLearning() }

        populateLearnCard()
        populateDeliveryCards()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as AppCompatActivity).supportActionBar!!.setHomeAsUpIndicator(0)
    }

    private fun observeStartLearning() {
        viewModel.startLearning.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DataState.Loading -> {
                    showLoading()
                }

                is DataState.Success -> {
                    hideLoading()
                    reportApiCall(true)

                    job.status = ConnectJobRecord.STATUS_LEARNING
                    ConnectJobUtils.upsertJob(job)

                    if (!hasLiveView()) return@observe
                    hideError()

                    launchApp(true)
                }

                is DataState.Error -> {
                    reportApiCall(false)
                    if (!hasLiveView()) return@observe
                    hideLoading()
                    val error =
                        PersonalIdOrConnectApiErrorHandler.handle(
                            requireActivity(),
                            state.errorCode,
                            state.throwable,
                        )
                    if (PersonalIdOrConnectApiErrorHandler.isNetworkError(state.errorCode)) {
                        showError(getString(R.string.failed_to_start_learning))
                    } else {
                        navigateToMessageDisplayDialog(
                            getString(R.string.error),
                            error,
                            false,
                            R.string.ok,
                        )
                    }
                }

                else -> {}
            }
        }
    }

    private fun populateLearnCard() {
        val modules = job.learnAppInfo.learnModules
        val totalHours = modules.sumOf { it.timeEstimate }

        binding.cardLearnModules.valueText = modules.size.toString()
        binding.cardLearnModules.titleText =
            resources.getQuantityString(
                R.plurals.connect_opportunity_learn_modules_label,
                modules.size,
            )
        binding.cardLearnModules.subtitleText =
            resources.getQuantityString(
                R.plurals.connect_opportunity_estimated_hours,
                totalHours,
                totalHours,
            )
        binding.cardLearnModules.onCardClick = {
            NavHostFragment.findNavController(this).navigate(
                ConnectJobIntroFragmentDirections
                    .actionConnectJobIntroFragmentToConnectLearnModulesBottomSheet(),
            )
        }
    }

    private fun populateDeliveryCards() {
        binding.cardMaxVisits.valueText = job.maxPossibleVisits.toString()
        binding.cardMaxVisits.subtitleText =
            getString(R.string.connect_opportunity_visits_per_day, job.maxDailyVisits)

        binding.cardDays.valueText = job.daysRemaining.toString()

        binding.cardMaxEarnings.valueText =
            ConnectMoneyUtils.moneyStringWithSymbol(job.currency, job.totalBudget)
        binding.cardMaxEarnings.subtitleText =
            resources.getQuantityString(
                R.plurals.connect_opportunity_payment_units,
                job.paymentUnits.size,
                job.paymentUnits.size,
            )
    }

    private fun startLearning() {
        viewModel.startLearning(job.jobUUID)
    }

    /** The learn app downloads into the same bar the user pressed to start learning. */
    override fun onInstallStateChanged(
        state: InstallState?,
        isLearning: Boolean,
    ) = binding.connectIntroCtaBar.renderInstallState(state, isLearning, ::forgetInstallFailure)

    private fun reportApiCall(success: Boolean) {
        FirebaseAnalyticsUtil.reportCccApiStartLearning(success)
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentConnectJobIntroBinding = FragmentConnectJobIntroBinding.inflate(inflater, container, false)

    private fun navigateToMessageDisplayDialog(
        title: String,
        message: String,
        isCancellable: Boolean,
        buttonText: Int,
    ) {
        val navDirections =
            ConnectJobIntroFragmentDirections
                .actionConnectJobIntroFragmentToPersonalidMessageDisplayDialog(
                    title,
                    message,
                    getString(buttonText),
                    null,
                ).setIsCancellable(isCancellable)
        NavHostFragment.findNavController(this).navigate(navDirections)
    }
}
