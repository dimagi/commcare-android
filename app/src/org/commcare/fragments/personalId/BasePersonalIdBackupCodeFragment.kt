package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentRecoveryCodeBinding
import org.commcare.views.connect.NumericCodeView

abstract class BasePersonalIdBackupCodeFragment : BasePersonalIdFragment() {
    protected lateinit var binding: FragmentRecoveryCodeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentRecoveryCodeBinding.inflate(inflater, container, false)
        clearBackupCodeFields()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.backupCodeView.requestFocus(requireActivity())
    }

    protected fun clearBackupCodeFields() {
        binding.backupCodeView.clearCode()
        binding.confirmCodeView.clearCode()
    }

    protected open fun submitIfEnabled() {
        if (binding.connectBackupCodeButton.isEnabled) handleBackupCodeSubmission()
    }

    abstract fun handleBackupCodeSubmission()

    protected fun togglePasswordVisibility(
        codeView: NumericCodeView,
        toggle: ImageView,
    ) {
        codeView.isPasswordVisible = !codeView.isPasswordVisible
        toggle.setImageResource(
            if (codeView.isPasswordVisible) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24,
        )
    }

    protected fun clearError() {
        binding.connectBackupCodeErrorMessage.visibility = View.GONE
        binding.connectBackupCodeErrorMessage.text = ""
    }

    protected fun showError(message: String) {
        binding.connectBackupCodeErrorMessage.visibility = View.VISIBLE
        binding.connectBackupCodeErrorMessage.text = message
    }

    protected fun enableContinueButton(enabled: Boolean) {
        binding.connectBackupCodeButton.isEnabled = enabled
    }

    protected open fun onCodeChanged() {
        validateBackupCodeAndEnableContinue()
    }

    protected open fun setupListeners() {
        binding.backupCodeView.setOnCodeChangedListener { onCodeChanged() }
        binding.backupCodeView.setOnEnterKeyPressedListener { submitIfEnabled() }
        binding.confirmCodeView.setOnCodeChangedListener { onCodeChanged() }
        binding.confirmCodeView.setOnEnterKeyPressedListener { submitIfEnabled() }
        binding.connectBackupCodeButton.setOnClickListener { handleBackupCodeSubmission() }
        binding.backupCodeVisibilityToggle.setOnClickListener {
            togglePasswordVisibility(binding.backupCodeView, binding.backupCodeVisibilityToggle)
        }
        binding.confirmCodeVisibilityToggle.setOnClickListener {
            togglePasswordVisibility(binding.confirmCodeView, binding.confirmCodeVisibilityToggle)
        }
    }

    protected fun validateBackupCodeAndEnableContinue() {
        enableContinueButton(validateBackupCodeInput())
    }

    protected fun validateBackupCodeInput(): Boolean {
        val backupCode = binding.backupCodeView.codeValue
        val isBackupCodeComplete = backupCode.length == BACKUP_CODE_LENGTH
        if (binding.confirmCodeLayout.visibility != View.VISIBLE) {
            enableContinueButton(isBackupCodeComplete)
            return true
        }
        val confirmCode = binding.confirmCodeView.codeValue
        val isConfirmCodeComplete = confirmCode.length == BACKUP_CODE_LENGTH
        if (isBackupCodeComplete && isConfirmCodeComplete && backupCode != confirmCode) {
            showError(getString(R.string.connect_backup_code_mismatch))
        } else {
            clearError()
        }
        val isValid = isBackupCodeComplete && backupCode == confirmCode
        enableContinueButton(isValid)
        return isValid
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        // no default implementation
    }

    companion object {
        const val BACKUP_CODE_LENGTH = 6
    }
}
