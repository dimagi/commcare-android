package org.commcare.fragments.personalId

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.ConnectConstants
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.personalId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.personalId.PersonalIdRecoveryCompleter
import org.commcare.personalId.PersonalIdUserPreferences

/**
 * Fragment for email verification during PersonalID configuration workflows - registration, recovery, and forgot backup code.
 */
class PersonalIdConfigurationEmailVerificationFragment : BasePersonalIdEmailVerificationFragment() {
    private var personalIdSessionData: PersonalIdSessionData? = null

    private fun args() = PersonalIdConfigurationEmailVerificationFragmentArgs.fromBundle(requireArguments())

    override fun resolveEmail(): String = args().email

    override fun displayEmail(): String = args().email

    override fun emailForApiCall(): String? = if (workflow == EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY) null else super.emailForApiCall()

    override fun resolveWorkflow(): EmailWorkFlow = args().workflow

    override fun resolveEmailOtpRequestCount(): Int = args().emailOtpRequestCount

    override fun onCreate(savedInstanceState: Bundle?) {
        personalIdSessionData =
            ViewModelProvider(requireActivity())
                .get(PersonalIdSessionDataViewModel::class.java)
                .personalIdSessionData
        super.onCreate(savedInstanceState)
    }

    override fun getSessionData(): PersonalIdSessionData? = personalIdSessionData

    override fun canSkipEmailVerification(): Boolean = true

    override fun doVerifyOtpRequest(otp: String) {
        if (workflow == EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY) {
            submitOtpViaCompleteRecovery(otp)
        } else {
            super.doVerifyOtpRequest(otp)
        }
    }

    private fun submitOtpViaCompleteRecovery(otp: String) {
        object : PersonalIdApiHandler<PersonalIdSessionData>() {
            override fun onSuccess(sessionData: PersonalIdSessionData) {
                if (!hasLiveView()) return
                if (sessionData.dbKey != null) {
                    onEmailVerified()
                } else {
                    onEmailVerificationFailure(
                        PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
                        null,
                    )
                }
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                if (!hasLiveView()) return
                onEmailVerificationFailure(errorCode, t)
            }
        }.completeRecoveryWithEmailOtp(requireActivity(), otp, personalIdSessionData!!)
    }

    override fun onMaxingEmailVerificationAttempts() {
        if (workflow == EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY) {
            navigateToMessageDisplay(
                getString(R.string.connect_backup_fail_title),
                getString(R.string.personalid_email_otp_max_attempts_reached),
                isCancellable = false,
                phase = ConnectConstants.PERSONALID_RECOVERY_EMAIL_OTP_FAILED,
                buttonText = R.string.ok,
            )
        } else {
            super.onMaxingEmailVerificationAttempts()
        }
    }

    override fun onEmailVerified() {
        when (workflow) {
            EmailWorkFlow.RECOVERY -> {
                personalIdSessionData!!.email = enteredEmail
                finalizeRecoveryAndShowSuccess()
            }

            EmailWorkFlow.REGISTRATION -> {
                personalIdSessionData!!.email = enteredEmail
                navigateToPhotoCapture()
            }

            EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY -> {
                finalizeRecovery()
                PersonalIdUserPreferences.setPendingBackupCode(true)
                binding.root.findNavController().navigate(
                    PersonalIdConfigurationEmailVerificationFragmentDirections
                        .actionPersonalidEmailVerificationToSetNewBackupCode(),
                )
            }

            else -> {
                throw IllegalStateException("Unexpected workflow: $workflow")
            }
        }
    }

    override fun proceedWithoutEmail() {
        when (workflow) {
            EmailWorkFlow.RECOVERY -> {
                finalizeRecoveryAndShowSuccess()
            }

            EmailWorkFlow.REGISTRATION -> {
                navigateToPhotoCapture()
            }

            else -> {
                throw IllegalArgumentException("Unexpected workflow: $workflow")
            }
        }
    }

    fun navigateToPhotoCapture() {
        binding.root.findNavController().navigate(
            PersonalIdConfigurationEmailVerificationFragmentDirections
                .actionPersonalidEmailVerificationToPersonalidPhotoCapture(),
        )
    }

    private fun finalizeRecoveryAndShowSuccess() {
        finalizeRecovery()
        navigateToRecoverySuccess()
    }

    private fun finalizeRecovery() {
        PersonalIdRecoveryCompleter.finalizeAccountRecovery(
            requireActivity(),
            personalIdSessionData!!,
        )
    }

    private fun navigateToRecoverySuccess() {
        navigateToMessageDisplay(
            getString(R.string.connect_recovery_success_title),
            getString(R.string.connect_recovery_success_message),
            isCancellable = false,
            phase = org.commcare.connect.ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
            buttonText = R.string.ok,
        )
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        binding.root.findNavController().navigate(
            PersonalIdConfigurationEmailVerificationFragmentDirections
                .actionPersonalidEmailVerificationToPersonalidMessage(
                    title,
                    message.orEmpty(),
                    phase,
                    getString(buttonText),
                    null,
                ).setIsCancellable(isCancellable),
        )
    }
}
