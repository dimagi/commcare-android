package org.commcare.personalId.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.BasePersonalIdBackupCodeFragment
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.personalId.PersonalIdUserPreferences

class PersonalIdProfileBackupCodeFragment : BasePersonalIdBackupCodeFragment() {
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
        val email = ConnectUserDatabaseUtil.getUser()?.email
        if (email != null) {
            val directions =
                PersonalIdProfileBackupCodeFragmentDirections
                    .actionProfileBackupCodeToSendEmailOtp(email, EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER)
                    .setMasked(true)
            findNavController().navigate(directions)
        } else {
            Toast
                .makeText(
                    requireContext(),
                    R.string.personalid_no_email_forgot_backup_code_toast,
                    Toast.LENGTH_LONG,
                ).show()
            findNavController().popBackStack()
        }
    }

    override fun handleBackupCodeSubmission() {
        val enteredCode = binding.backupCodeView.codeValue
        val storedBackupCode = ConnectUserDatabaseUtil.getUser()?.pin
        if (enteredCode == storedBackupCode) {
            PersonalIdUserPreferences.clearBackupCodeLockout()
            findNavController().navigate(R.id.action_profile_backup_code_to_set_new_backup_code)
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
