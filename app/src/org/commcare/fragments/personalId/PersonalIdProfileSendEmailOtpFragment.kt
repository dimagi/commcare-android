package org.commcare.fragments.personalId

import androidx.navigation.findNavController
import org.commcare.android.database.connect.models.PersonalIdSessionData

/**
 * Screen that sends an email OTP to the user during the profile (existing-user) flow and
 * navigates to the verification screen. No session token is needed; auth is via stored user
 * credentials.
 */
class PersonalIdProfileSendEmailOtpFragment : BasePersonalIdSendEmailOtpFragment() {
    override fun getSessionData(): PersonalIdSessionData? = null

    override fun navigateToVerification() {
        val directions =
            PersonalIdProfileSendEmailOtpFragmentDirections
                .actionPersonalidSendEmailOtpToEmailVerification(email, emailOtpTracker.requestCount)
        binding.root.findNavController().navigate(directions)
    }
}
