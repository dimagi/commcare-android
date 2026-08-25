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

    protected  open fun submitIfEnabled() {
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

    companion object {
        const val BACKUP_CODE_LENGTH = 6
    }
}
