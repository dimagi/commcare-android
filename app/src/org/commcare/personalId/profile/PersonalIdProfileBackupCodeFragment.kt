package org.commcare.personalId.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.BasePersonalIdBackupCodeFragment
import org.commcare.fragments.personalId.EmailWorkFlow

class PersonalIdProfileBackupCodeFragment : BasePersonalIdBackupCodeFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        setUpView()
        return view
    }

    fun setUpView() {
        requireActivity().title = getString(R.string.connect_backup_code_title_confirm)
        binding.recoveryCodeTilte.setText(R.string.connect_backup_code_title_confirm)
        binding.backupCodeSubtitle.setText(R.string.connect_backup_code_message)
        binding.backupCodeLayout.visibility = View.VISIBLE
        binding.confirmCodeLayout.visibility = View.GONE
        binding.confirmCodeLabel.visibility = View.GONE
        binding.welcomeBackLayout.visibility = View.GONE
        binding.notMeButton.visibility = View.VISIBLE
        binding.notMeButton.setText(R.string.personalid_forgot_backup_code)
        enableContinueButton(false)
        setupListeners()
    }

    private fun setupListeners() {
        binding.backupCodeView.setOnCodeChangedListener { validateCode() }
        binding.backupCodeView.setOnEnterKeyPressedListener { submitIfEnabled() }
        binding.connectBackupCodeButton.setOnClickListener { handleBackupCodeSubmission() }
        binding.notMeButton.setOnClickListener { handleForgot() }
        binding.backupCodeVisibilityToggle.setOnClickListener {
            togglePasswordVisibility(binding.backupCodeView, binding.backupCodeVisibilityToggle)
        }
    }

    private fun validateCode() {
        enableContinueButton(binding.backupCodeView.codeValue.length == BACKUP_CODE_LENGTH)
    }

    private fun handleForgot() {
        val email = ConnectUserDatabaseUtil.getUser(requireContext())?.email
        if (email != null) {
            findNavController().navigate(
                PersonalIdProfileBackupCodeFragmentDirections
                    .actionProfileBackupCodeToEmailVerification(
                        email = email,
                        workflow = EmailWorkFlow.RECOVERY,
                        emailOtpRequestCount = 0,
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

    override fun handleBackupCodeSubmission() {
        val enteredCode = binding.backupCodeView.codeValue
        val storedBackupCode = ConnectUserDatabaseUtil.getUser(requireContext())?.pin
        if (enteredCode == storedBackupCode) {
            findNavController().navigate(R.id.action_profile_backup_code_to_set_new_backup_code)
        } else {
            showError(getString(R.string.connect_backup_fail_title))
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        // unreachable
    }
}
