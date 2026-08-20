package org.commcare.personalId.profile

import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.BasePersonalIdBackupCodeFragment
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.views.connect.NumericCodeView

class PersonalIdProfileBackupCodeFragment : BasePersonalIdBackupCodeFragment() {
    override fun onBindingCreated() {
        requireActivity().title = getString(R.string.connect_backup_code_title_confirm)

        binding.recoveryCodeTilte.setText(R.string.connect_backup_code_title_confirm)
        binding.backupCodeSubtitle.setText(R.string.personalid_confirm_backup_code_subtitle)
        binding.backupCodeLayout.visibility = View.VISIBLE
        binding.confirmCodeLabel.visibility = View.GONE
        binding.confirmCodeLayout.visibility = View.GONE
        binding.welcomeBackLayout.visibility = View.GONE

        binding.notMeButton.visibility = View.VISIBLE
        binding.notMeButton.setText(R.string.personalid_forgot_backup_code)

        enableContinueButton(false)
        setupListeners()
    }

    private fun setupListeners() {
        binding.backupCodeView.setOnCodeChangedListener(
            NumericCodeView.OnCodeChangedListener { validateCode() },
        )
        binding.backupCodeView.setCodeCompleteListener { submitIfEnabled() }
        binding.backupCodeView.setOnEnterKeyPressedListener(
            NumericCodeView.OnEnterKeyPressedListener { submitIfEnabled() },
        )
        binding.connectBackupCodeButton.setOnClickListener { handleSubmit() }
        binding.notMeButton.setOnClickListener { handleForgot() }
        binding.backupCodeVisibilityToggle.setOnClickListener {
            togglePasswordVisibility(binding.backupCodeView, binding.backupCodeVisibilityToggle)
        }
    }

    private fun validateCode() {
        enableContinueButton(binding.backupCodeView.codeValue.length == BACKUP_CODE_LENGTH)
    }

    private fun submitIfEnabled() {
        if (binding.connectBackupCodeButton.isEnabled) handleSubmit()
    }

    private fun handleSubmit() {
        val enteredCode = binding.backupCodeView.codeValue
        val storedPassword = ConnectUserDatabaseUtil.getUser(requireContext())?.password
        if (enteredCode == storedPassword) {
            findNavController().navigate(R.id.action_profile_backup_code_to_set_new_backup_code)
        } else {
            showError(getString(R.string.personalid_confirm_backup_code_wrong))
            enableContinueButton(true)
        }
    }

    private fun handleForgot() {
        val user = ConnectUserDatabaseUtil.getUser(requireContext())
        val email = user?.email
        if (email != null) {
            findNavController().navigate(
                PersonalIdProfileBackupCodeFragmentDirections
                    .actionProfileBackupCodeToEmailVerification(
                        email,
                        EmailWorkFlow.RECOVERY,
                        0,
                    ),
            )
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

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        // Profile backup-code flow does not make API calls; this path is unreachable.
    }
}
