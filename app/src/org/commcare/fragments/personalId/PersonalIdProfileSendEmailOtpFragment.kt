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
        } else if (workflow == EmailWorkFlow.EXISTING_USER) {
            binding.personalidSendEmailOtpTitle.setText(R.string.personalid_email_verification_title)
        }
    }

    override fun setAppBarTitle() {
        val titleRes =
            if (workflow == EmailWorkFlow.PENDING_BACKUP_CODE) {
                R.string.personalid_send_email_otp_pending_backup_code_title
            } else  if (workflow == EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER) {
                R.string.personalid_send_email_otp_title
            } else {
                R.string.personalid_email_verification_title
            }
        setTitle(titleRes)
    }

    override fun navigateToVerification() {
        val directions =
            when (workflow) {
                EmailWorkFlow.EXISTING_USER -> {
                    PersonalIdProfileSendEmailOtpFragmentDirections
                        .actionPersonalidSendEmailOtpToProfileEmailVerification(
                            email,
                            EmailWorkFlow.EXISTING_USER,
                            emailOtpTracker.requestCount,
                        )
                }

                EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER, EmailWorkFlow.PENDING_BACKUP_CODE -> {
                    PersonalIdProfileSendEmailOtpFragmentDirections
                        .actionPersonalidSendEmailOtpToEmailVerificationForgotBackupCode(
                            email,
                            emailOtpTracker.requestCount
                        )
                }

                else -> {
                    throw IllegalStateException("Unexpected workflow in PersonalIdProfileSendEmailOtpFragment: $workflow")
                }
            }
        binding.root.findNavController().navigate(directions)
    }
}
