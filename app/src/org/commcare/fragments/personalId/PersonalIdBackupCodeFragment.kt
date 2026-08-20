package org.commcare.fragments.personalId

import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDirections
import androidx.navigation.findNavController
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.ConnectConstants
import org.commcare.connect.ReleaseToggleHelper
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdRecoveryCompleter
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.utils.MediaUtil
import org.commcare.views.connect.NumericCodeView
import java.util.Date

class PersonalIdBackupCodeFragment : BasePersonalIdBackupCodeFragment() {
    private lateinit var personalIdSessionData: PersonalIdSessionData
    private var isRecovery = false

    @StringRes
    private var titleId = 0

    override fun onBindingCreated() {
        personalIdSessionData =
            ViewModelProvider(requireActivity())[PersonalIdSessionDataViewModel::class.java]
                .personalIdSessionData
        configureUiByMode()
        setupListeners()
        requireActivity().title = getString(titleId)
    }

    override fun onResume() {
        super.onResume()
        validateBackupCodeInputs()
    }

    private fun configureUiByMode() {
        isRecovery = personalIdSessionData.accountExists == true
        if (isRecovery) {
            titleId = R.string.connect_backup_code_title_confirm
            binding.recoveryCodeTilte.setText(R.string.connect_backup_code_message_title)
            binding.backupCodeSubtitle.setText(R.string.connect_backup_code_message)
            binding.backupCodeLayout.visibility = View.VISIBLE
            binding.confirmCodeLabel.visibility = View.GONE
            binding.confirmCodeLayout.visibility = View.GONE
            setUserNameAndPhoto()
        } else {
            titleId = R.string.connect_backup_code_title_set
            binding.backupCodeSubtitle.text = getString(R.string.connect_backup_code_remember, BACKUP_CODE_LENGTH)
            binding.backupCodeLayout.visibility = View.VISIBLE
            binding.confirmCodeLabel.visibility = View.VISIBLE
            binding.confirmCodeLayout.visibility = View.VISIBLE
            binding.welcomeBackLayout.visibility = View.GONE
        }
    }

    private fun setUserNameAndPhoto() {
        binding.welcomeBack.text = getString(R.string.personalid_welcome_back_msg, personalIdSessionData.userName)
        val photoBase64 = personalIdSessionData.photoBase64
        if (!photoBase64.isNullOrEmpty()) {
            binding.userPhoto.setImageBitmap(MediaUtil.decodeBase64EncodedBitmap(photoBase64))
        }
    }

    private fun setupListeners() {
        val codeChangedListener = NumericCodeView.OnCodeChangedListener { validateBackupCodeInputs() }

        binding.backupCodeView.setOnCodeChangedListener(codeChangedListener)
        binding.confirmCodeView.setOnCodeChangedListener(codeChangedListener)

        binding.backupCodeView.setCodeCompleteListener {
            if (isRecovery) {
                submitIfEnabled()
            }
        }

        binding.confirmCodeView.setCodeCompleteListener { submitIfEnabled() }

        val enterKeyListener = NumericCodeView.OnEnterKeyPressedListener { submitIfEnabled() }
        binding.backupCodeView.setOnEnterKeyPressedListener(enterKeyListener)
        binding.confirmCodeView.setOnEnterKeyPressedListener(enterKeyListener)

        binding.connectBackupCodeButton.setOnClickListener { handleBackupCodeSubmission() }
        binding.notMeButton.setOnClickListener { handleNotMeButtonPressed() }

        binding.backupCodeVisibilityToggle.setOnClickListener {
            togglePasswordVisibility(binding.backupCodeView, binding.backupCodeVisibilityToggle)
        }
        binding.confirmCodeVisibilityToggle.setOnClickListener {
            togglePasswordVisibility(binding.confirmCodeView, binding.confirmCodeVisibilityToggle)
        }
    }

    private fun submitIfEnabled() {
        if (binding.connectBackupCodeButton.isEnabled) {
            handleBackupCodeSubmission()
        }
    }

    private fun validateBackupCodeInputs() {
        val backupCode = binding.backupCodeView.codeValue
        val confirmCode = binding.confirmCodeView.codeValue

        val isCodeComplete = backupCode.length == BACKUP_CODE_LENGTH
        val isCodeConfirmed = isRecovery || backupCode == confirmCode
        val showMismatch = isCodeComplete && !isCodeConfirmed && confirmCode.length == BACKUP_CODE_LENGTH
        val errorText = if (showMismatch) getString(R.string.connect_backup_code_mismatch) else ""

        binding.backupCodeErrorBox.visibility = if (showMismatch) View.VISIBLE else View.GONE
        binding.backupCodeErrorText.text = errorText
        enableContinueButton(isCodeComplete && isCodeConfirmed)
    }

    private fun handleNotMeButtonPressed() {
        personalIdSessionData.accountExists = false
        clearBackupCodeFields()
        configureUiByMode()
    }

    private fun handleBackupCodeSubmission() {
        FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(
            javaClass.simpleName,
            null,
            PersonalIdWorkflow.CONFIGURATION,
        )
        personalIdSessionData.backupCode = binding.backupCodeView.codeValue
        if (isRecovery) {
            confirmBackupCode()
        } else {
            if (ReleaseToggleHelper.isEmailOtpVerificationActive(personalIdSessionData)) {
                navigateToEmail()
            } else {
                navigateToPhoto()
            }
        }
    }

    private fun confirmBackupCode() {
        clearError()
        enableContinueButton(false)
        val backupCode = binding.backupCodeView.codeValue

        object : PersonalIdApiHandler<PersonalIdSessionData>() {
            override fun onSuccess(sessionData: PersonalIdSessionData) {
                if (sessionData.dbKey != null) {
                    handleConfirmBackupCodeSuccess()
                } else if ((sessionData.attemptsLeft ?: 0) > 0) {
                    handleFailedBackupCodeAttempt()
                }
            }

            override fun onFailure(
                failureCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                if (handleCommonSignupFailures(failureCode)) {
                    return
                }
                showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t))
                if (failureCode.shouldAllowRetry()) {
                    enableContinueButton(true)
                }
            }
        }.confirmBackupCode(activity, backupCode, personalIdSessionData)
    }

    private fun handleConfirmBackupCodeSuccess() {
        if (personalIdSessionData.email == null &&
            ReleaseToggleHelper.isEmailOtpVerificationActive(personalIdSessionData)
        ) {
            navigateToEmail()
        } else {
            PersonalIdRecoveryCompleter.finalizeAccountRecovery(requireActivity(), personalIdSessionData)
            navigateToSuccess()
        }
    }

    private fun handleFailedBackupCodeAttempt() {
        logRecoveryFailureResult()
        clearBackupCodeFields()
        navigateWithMessage(
            getString(R.string.connect_backup_fail_title),
            getString(R.string.personalid_wrong_backup_message, personalIdSessionData.attemptsLeft),
            ConnectConstants.PERSONALID_RECOVERY_WRONG_BACKUPCODE,
        )
    }

    private fun navigateToEmail() {
        PersonalIdUserPreferences.setLastEmailOfferDate(Date())
        val emailWorkFlow = if (isRecovery) EmailWorkFlow.RECOVERY else EmailWorkFlow.REGISTRATION
        navigate(
            PersonalIdBackupCodeFragmentDirections
                .actionPersonalidBackupcodeToPersonalidEmail(emailWorkFlow),
        )
    }

    private fun logRecoveryFailureResult() {
        FirebaseAnalyticsUtil.reportPersonalIdAccountRecovered(
            false,
            AnalyticsParamValue.CCC_RECOVERY_METHOD_BACKUPCODE,
        )
    }

    private fun navigateWithMessage(
        title: String,
        message: String,
        phase: Int,
    ) {
        navigateToMessageDisplay(title, message, false, phase, R.string.ok)
    }

    private fun navigateToPhoto() {
        navigate(PersonalIdBackupCodeFragmentDirections.actionPersonalidBackupcodeToPersonalidPhotoCapture())
    }

    private fun navigateToSuccess() {
        navigateWithMessage(
            getString(R.string.connect_recovery_success_title),
            getString(R.string.connect_recovery_success_message),
            ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
        )
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        navigate(
            PersonalIdBackupCodeFragmentDirections
                .actionPersonalidBackupcodeToPersonalidMessage(
                    title,
                    message.orEmpty(),
                    phase,
                    getString(buttonText),
                    null,
                ).setIsCancellable(isCancellable),
        )
    }

    private fun navigate(directions: NavDirections) = binding.root.findNavController().navigate(directions)
}
