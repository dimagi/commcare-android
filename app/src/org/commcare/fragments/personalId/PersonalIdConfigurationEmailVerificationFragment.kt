package org.commcare.fragments.personalId

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.commcare.personalId.PersonalIdRecoveryCompleter

/**
 * Fragment for email verification during PersonalID configuration workflows - registration and recovery.
 */
class PersonalIdConfigurationEmailVerificationFragment : BasePersonalIdEmailVerificationFragment() {
    private var personalIdSessionData: PersonalIdSessionData? = null

    private fun args() = PersonalIdConfigurationEmailVerificationFragmentArgs.fromBundle(requireArguments())

    override fun resolveEmail(): String = args().email

    override fun displayEmail(): String = args().email

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
