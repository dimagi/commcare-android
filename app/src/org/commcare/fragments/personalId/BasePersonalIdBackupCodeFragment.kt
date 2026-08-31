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
        initData()
        setUpView()
        setupListeners()
        clearBackupCodeFields()
        return binding.root
    }

    open fun initData() {}

    abstract fun setUpView()

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

    protected fun setUpInitialState(
        titleResId: Int,
        showConfirmCode: Boolean,
        subtitle: CharSequence,
        notMeButtonTextId: Int? = null,
    ) {
        requireActivity().title = getString(titleResId)
        binding.recoveryCodeTilte.setText(titleResId)
        binding.backupCodeLayout.visibility = View.VISIBLE
        binding.welcomeBackLayout.visibility = View.GONE
        binding.notMeButton.visibility = View.GONE
        val confirmVisibility = if (showConfirmCode) View.VISIBLE else View.GONE
        binding.confirmCodeLayout.visibility = confirmVisibility
        binding.confirmCodeLabel.visibility = confirmVisibility
        binding.backupCodeSubtitle.text = subtitle
        notMeButtonTextId?.let {
            binding.notMeButton.setText(getString(it))
            binding.notMeButton.visibility = View.VISIBLE
        }
        enableContinueButton(false)
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
            return isBackupCodeComplete
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
