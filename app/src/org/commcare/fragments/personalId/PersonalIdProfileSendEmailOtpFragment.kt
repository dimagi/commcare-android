package org.commcare.fragments.personalId

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R

/**
 * Screen that sends an email OTP to the user during the profile (existing-user) flow and
 * navigates to the verification screen. No session token is needed; auth is via stored user
 * credentials.
 */
class PersonalIdProfileSendEmailOtpFragment : BasePersonalIdSendEmailOtpFragment() {
    override fun getSessionData(): PersonalIdSessionData? = null

    override fun setUpView() {
        super.setUpView()
        if (workflow == EmailWorkFlow.PENDING_BACKUP_CODE) {
            binding.personalidSendEmailOtpTitle.setText(R.string.personalid_send_email_otp_pending_backup_code_title)
            binding.personalidSendEmailOtpSubtitle.setText(R.string.personalid_send_email_otp_pending_backup_code_subtitle)
            binding.personalidSendEmailOtpSubtitle.visibility = View.VISIBLE
        }
    }

    override fun setAppBarTitle() {
        val titleRes =
            if (workflow == EmailWorkFlow.PENDING_BACKUP_CODE) {
                R.string.personalid_send_email_otp_pending_backup_code_title
            } else {
                R.string.personalid_send_email_otp_title
            }
        setTitle(titleRes)
    }

    override fun navigateToVerification() {
        val directions =
            PersonalIdProfileSendEmailOtpFragmentDirections
                .actionPersonalidSendEmailOtpToEmailVerification(email, emailOtpTracker.requestCount)
        binding.root.findNavController().navigate(directions)
    }
}
