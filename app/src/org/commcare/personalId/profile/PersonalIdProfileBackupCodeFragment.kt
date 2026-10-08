package org.commcare.personalId.profile

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.BasePersonalIdBackupCodeFragment
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.utils.AccountSecurityAnalyticsMapper

class PersonalIdProfileBackupCodeFragment : BasePersonalIdBackupCodeFragment() {
    private val args by lazy { PersonalIdProfileBackupCodeFragmentArgs.fromBundle(requireArguments()) }
    private val pendingEmail get() = args.pendingEmail

    private var isLocked = false

    private val analyticsWorkflow get() = AccountSecurityAnalyticsMapper.workflowParam(args.backupCodeWorkflow)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        if (PersonalIdUserPreferences.isBackupCodeLockedOut()) {
            enterLockedState()
        }
    }

    override fun setUpView() {
        setUpInitialState(
            titleResId = R.string.connect_backup_code_title_confirm,
            showConfirmCode = false,
            subtitle = getString(R.string.connect_backup_code_message),
        )
        binding.personalidForgotBackupCode.visibility = View.VISIBLE
        FirebaseAnalyticsUtil.reportPersonalIdAccountSecurityAction(
            analyticsWorkflow,
            AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_CONFIRM_CODE_SHOWN,
            null,
            null,
            null,
        )
    }

    override fun onCodeChanged() {
        if (!isLocked) validateBackupCodeAndEnableContinue()
    }

    override fun handleForgotBackupCode() {
        FirebaseAnalyticsUtil.reportPersonalIdAccountSecurityAction(
            analyticsWorkflow,
            AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_FORGOT_CODE_STARTED,
            null,
            null,
            null,
        )
        val email = ConnectUserDatabaseUtil.getUser().email
        when {
            !email.isNullOrEmpty() -> {
                navigateToForgotBackupCodeEmailOtp(email, EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER)
            }

            isAddingEmail() -> {
                findNavController().navigate(
                    PersonalIdProfileBackupCodeFragmentDirections
                        .actionProfileBackupCodeToProfileSendPhoneOtp(pendingEmail),
                )
            }

            else -> {
                (requireActivity() as PersonalIdProfileActivity).showAddEmailToast()
                findNavController().popBackStack()
            }
        }
    }

    /** Adding an email from Manage Profile: the gate was opened with a pending email to verify. */
    private fun isAddingEmail() = args.emailWorkflow == EmailWorkFlow.EXISTING_USER && pendingEmail.isNotEmpty()

    private fun navigateToForgotBackupCodeEmailOtp(
        email: String,
        workflow: EmailWorkFlow,
    ) {
        findNavController().navigate(
            PersonalIdProfileBackupCodeFragmentDirections
                .actionProfileBackupCodeToSendEmailOtp(email, workflow),
        )
    }

    override fun handleBackupCodeSubmission() {
        val enteredCode = binding.backupCodeView.codeValue
        val storedBackupCode = ConnectUserDatabaseUtil.getUser()?.pin
        if (enteredCode == storedBackupCode) {
            PersonalIdUserPreferences.clearBackupCodeLockout()
            reportConfirmAttempt(AnalyticsParamValue.OTP_OUTCOME_SUCCESS, null)
            if (args.emailWorkflow == EmailWorkFlow.EXISTING_USER) {
                navigateToEmailOtpFragment()
            } else {
                navigateToSetNewBackupCode()
            }
        } else {
            val attempts = PersonalIdUserPreferences.recordBackupCodeFailure()
            reportConfirmAttempt(AnalyticsParamValue.OTP_OUTCOME_FAILURE, attempts)
            if (attempts >= MAX_ATTEMPTS) {
                FirebaseAnalyticsUtil.reportPersonalIdAccountSecurityAction(
                    analyticsWorkflow,
                    AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_CONFIRM_CODE_MAX_ATTEMPTS,
                    null,
                    null,
                    attempts,
                )
                PersonalIdUserPreferences.triggerBackupCodeLockout()
                enterLockedState()
            } else {
                showError(getString(R.string.connect_backup_fail_title))
            }
        }
    }

    private fun navigateToEmailOtpFragment() {
        val directions =
            PersonalIdProfileBackupCodeFragmentDirections
                .actionProfileBackupCodeToSendEmailOtp(pendingEmail, args.emailWorkflow)
                .setMasked(false)
        findNavController().navigate(directions)
    }

    private fun reportConfirmAttempt(
        outcome: String,
        failedAttempts: Int?,
    ) {
        FirebaseAnalyticsUtil.reportPersonalIdAccountSecurityAction(
            analyticsWorkflow,
            AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_CONFIRM_CODE_ATTEMPT,
            outcome,
            null,
            failedAttempts,
        )
    }

    private fun navigateToSetNewBackupCode() {
        val directions =
            PersonalIdProfileBackupCodeFragmentDirections
                .actionProfileBackupCodeToSetNewBackupCode(args.backupCodeWorkflow)
        findNavController().navigate(directions)
    }

    private fun enterLockedState() {
        isLocked = true
        showError(getString(R.string.personalid_backup_code_too_many_attempts))
        binding.backupCodeView.isEnabled = false
        enableContinueButton(false)
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }
}
