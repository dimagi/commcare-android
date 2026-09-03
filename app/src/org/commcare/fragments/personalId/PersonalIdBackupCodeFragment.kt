package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDirections
import androidx.navigation.findNavController
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.ConnectConstants
import org.commcare.connect.ReleaseToggleHelper
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.personalId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdRecoveryCompleter
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.utils.MediaUtil
import java.util.Date

class PersonalIdBackupCodeFragment : BasePersonalIdBackupCodeFragment() {
    private lateinit var personalIdSessionData: PersonalIdSessionData
    private var isRecovery = false

    override fun onResume() {
        super.onResume()
        validateBackupCodeAndEnableContinue()
    }

    override fun initData() {
        personalIdSessionData =
            ViewModelProvider(requireActivity())[PersonalIdSessionDataViewModel::class.java]
                .personalIdSessionData
    }

    override fun setUpView() {
        isRecovery = personalIdSessionData.accountExists == true
        if (isRecovery) {
            setUpInitialState(
                titleResId = R.string.connect_backup_code_title_confirm,
                showConfirmCode = false,
                subtitle = getString(R.string.connect_backup_code_message),
            )
            binding.recoveryCodeTilte.setText(R.string.connect_backup_code_message_title)
            binding.welcomeBackLayout.visibility = View.VISIBLE
            setUserNameAndPhoto()
            binding.personalidForgotBackupCode.visibility =
                if (!personalIdSessionData.email.isNullOrEmpty()) View.VISIBLE else View.GONE
        } else {
            setUpInitialState(
                titleResId = R.string.connect_backup_code_title_set,
                showConfirmCode = true,
                subtitle = getString(R.string.connect_backup_code_remember, BACKUP_CODE_LENGTH),
            )
        }
    }

    private fun setUserNameAndPhoto() {
        binding.welcomeBack.text = getString(R.string.personalid_welcome_back_msg, personalIdSessionData.userName)
        val photoBase64 = personalIdSessionData.photoBase64
        if (!photoBase64.isNullOrEmpty()) {
            binding.userPhoto.setImageBitmap(MediaUtil.decodeBase64EncodedBitmap(photoBase64))
        }
    }

    override fun setupListeners() {
        super.setupListeners()
        binding.backupCodeView.setCodeCompleteListener { if (isRecovery) submitIfEnabled() }
        binding.confirmCodeView.setCodeCompleteListener { submitIfEnabled() }
        binding.notMeButton.setOnClickListener { handleNotMeButtonPressed() }
        binding.personalidForgotBackupCode.setOnClickListener { handleForgotBackupCode() }
    }

    private fun handleNotMeButtonPressed() {
        personalIdSessionData.accountExists = false
        clearBackupCodeFields()
        setUpView()
    }

    private fun handleForgotBackupCode() {
        val bundle =
            Bundle().apply {
                putString("email", personalIdSessionData.email!!)
                putBoolean("masked", true)
                putSerializable("workflow", EmailWorkFlow.BACKUP_CODE_RECOVERY_SIGN_IN)
            }
        binding.root
            .findNavController()
            .navigate(R.id.action_personalid_backup_code_to_send_email_otp, bundle)
    }

    override fun handleBackupCodeSubmission() {
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
