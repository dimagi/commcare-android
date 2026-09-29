package org.commcare.fragments.personalId

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.core.network.AuthInfo
import org.commcare.dalvik.R
import org.commcare.utils.AttemptCounter

/**
 * Phone OTP screen in the PersonalID signup / recovery configuration flow. Authenticates with the
 * PersonalID session token and continues to name entry.
 */
class PersonalIdPhoneVerificationFragment : BasePersonalIdPhoneVerificationFragment() {
    private val personalIdSessionData: PersonalIdSessionData by lazy {
        ViewModelProvider(requireActivity())[PersonalIdSessionDataViewModel::class.java].personalIdSessionData
    }

    private val sessionAttemptCounter =
        object : AttemptCounter {
            override val requestCount get() = personalIdSessionData.otpAttempts
            override val failedAttempts get() = personalIdSessionData.otpVerificationFailedAttempts

            override fun recordRequest() {
                personalIdSessionData.otpAttempts = personalIdSessionData.otpAttempts + 1
            }

            override fun recordFailedAttempt() {
                personalIdSessionData.otpVerificationFailedAttempts =
                    personalIdSessionData.otpVerificationFailedAttempts + 1
            }
        }

    override fun getPhoneNumber(): String? = personalIdSessionData.phoneNumber

    override fun buildAuthInfo(): AuthInfo = AuthInfo.TokenAuth(personalIdSessionData.token)

    override fun sessionDataOrNull(): PersonalIdSessionData = personalIdSessionData

    override fun defaultSmsMethod(): String? = personalIdSessionData.smsMethod

    override fun isFallbackAllowed(): Boolean = personalIdSessionData.otpFallback

    override fun attemptCounter(): AttemptCounter = sessionAttemptCounter

    override fun analyticsWorkflow(): EmailWorkFlow = EmailWorkFlow.REGISTRATION

    override fun continueClickedWorkflow(): PersonalIdWorkflow = PersonalIdWorkflow.CONFIGURATION

    override fun handleApiFailure(failureCode: PersonalIdOrConnectApiErrorCodes): Boolean = handleCommonSignupFailures(failureCode)

    override fun onChangeNumberClicked() {
        Navigation.findNavController(binding.root).popBackStack(R.id.personalid_phone_fragment, false)
    }

    override fun onOtpVerified() {
        Navigation
            .findNavController(binding.root)
            .navigate(PersonalIdPhoneVerificationFragmentDirections.actionPersonalidOtpPageToPersonalidName())
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        val directions =
            PersonalIdPhoneVerificationFragmentDirections
                .actionPersonalidOtpPageToPersonalidMessage(title, message.orEmpty(), phase, getString(buttonText), null)
                .setIsCancellable(isCancellable)
        Navigation.findNavController(binding.root).navigate(directions)
    }
}
