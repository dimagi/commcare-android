package org.commcare.personalId.profile

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.core.network.AuthInfo
import org.commcare.fragments.personalId.BasePersonalIdPhoneVerificationFragment
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.fragments.personalId.PersonalIdWorkflow
import org.commcare.utils.AttemptCounter
import org.commcare.utils.CommCareAttemptCounter
import org.commcare.utils.OtpManager

/**
 * Phone OTP screen in Manage Profile. Stands in for the backup code on an email change when none is
 * stored, or when a user with no email on file forgets it while adding one. Authenticates with the
 * stored user's credentials and continues to Send email OTP for the pending email.
 */
class PersonalIdProfilePhoneVerificationFragment : BasePersonalIdPhoneVerificationFragment() {
    private val args by lazy { PersonalIdProfilePhoneVerificationFragmentArgs.fromBundle(requireArguments()) }
    private val user: ConnectUserRecord by lazy { ConnectUserDatabaseUtil.getUser() }
    private lateinit var attemptTracker: CommCareAttemptCounter

    override fun onCreate(savedInstanceState: Bundle?) {
        attemptTracker =
            CommCareAttemptCounter(
                savedInstanceState?.getInt(KEY_REQUEST_COUNT) ?: 0,
                savedInstanceState?.getInt(KEY_FAILED_ATTEMPTS) ?: 0,
            )
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_REQUEST_COUNT, attemptTracker.requestCount)
        outState.putInt(KEY_FAILED_ATTEMPTS, attemptTracker.failedAttempts)
    }

    override fun getPhoneNumber(): String? = user.primaryPhone

    override fun buildAuthInfo(): AuthInfo = AuthInfo.ProvidedAuth(user.userId, user.password, false)

    override fun defaultSmsMethod(): String = OtpManager.SMS_METHOD_FIREBASE

    override fun isFallbackAllowed(): Boolean = true

    override fun attemptCounter(): AttemptCounter = attemptTracker

    override fun analyticsWorkflow(): EmailWorkFlow = EmailWorkFlow.EXISTING_USER

    override fun continueClickedWorkflow(): PersonalIdWorkflow = PersonalIdWorkflow.EDIT_PROFILE

    override fun showChangeNumberLink(): Boolean = false

    override fun onOtpVerified() {
        findNavController().navigate(
            PersonalIdProfilePhoneVerificationFragmentDirections
                .actionProfilePhoneVerificationToSendEmailOtp(args.pendingEmail, EmailWorkFlow.EXISTING_USER)
                .setMasked(false),
        )
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ): Unit = throw IllegalStateException("navigateToMessageDisplay should not have a call path in this fragment")

    companion object {
        private const val KEY_REQUEST_COUNT = "KEY_PROFILE_OTP_REQUEST_COUNT"
        private const val KEY_FAILED_ATTEMPTS = "KEY_PROFILE_OTP_FAILED_ATTEMPTS"
    }
}
