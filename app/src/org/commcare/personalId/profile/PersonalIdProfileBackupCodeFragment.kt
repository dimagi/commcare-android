package org.commcare.personalId.profile

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.BasePersonalIdBackupCodeFragment
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.personalId.PersonalIdUserPreferences

class PersonalIdProfileBackupCodeFragment : BasePersonalIdBackupCodeFragment() {
    private val args by lazy { PersonalIdProfileBackupCodeFragmentArgs.fromBundle(requireArguments()) }
    private val pendingEmail get() = args.pendingEmail

    private var isLocked = false

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
    }

    override fun onCodeChanged() {
        if (!isLocked) validateBackupCodeAndEnableContinue()
    }

    override fun handleForgotBackupCode() {
        val email = ConnectUserDatabaseUtil.getUser().email
        if (email.isNullOrEmpty()) {
            (requireActivity() as PersonalIdProfileActivity).showAddEmailToast()
            findNavController().popBackStack()
        } else {
            navigateToForgotBackupCodeEmailOtp(email, EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER)
        }
    }

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
            if (args.emailWorkflow == EmailWorkFlow.EXISTING_USER) {
                navigateToEmailOtpFragment()
            } else {
                navigateToSetNewBackupCode()
            }
        } else {
            val attempts = PersonalIdUserPreferences.recordBackupCodeFailure()
            if (attempts >= MAX_ATTEMPTS) {
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
        findNavController().navigate(directions)
    }

    private fun navigateToSetNewBackupCode() {
        val directions = PersonalIdProfileBackupCodeFragmentDirections.actionProfileBackupCodeToSetNewBackupCode()
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
