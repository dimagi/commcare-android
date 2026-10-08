package org.commcare.fragments.personalId

import androidx.navigation.findNavController
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.AccountSecurityAnalyticsMapper

/**
 * Email verification fragment for the forgot backup code flow for already signed-in users.
 */
class PersonalIdEmailVerificationForgotBackupCodeFragment : BasePersonalIdEmailVerificationFragment() {
    private fun args() = PersonalIdEmailVerificationForgotBackupCodeFragmentArgs.fromBundle(requireArguments())

    override fun resolveEmail(): String = args().email

    override fun displayEmail(): String = EmailHelper.maskEmail(args().email)

    override fun resolveWorkflow(): EmailWorkFlow = EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER

    override fun resolveEmailOtpRequestCount(): Int = args().emailOtpRequestCount

    override fun onEmailVerified() {
        FirebaseAnalyticsUtil.reportPersonalIdAccountSecurityAction(
            AccountSecurityAnalyticsMapper.workflowParam(resolveWorkflow()),
            AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_RECOVERY_COMPLETED,
            AnalyticsParamValue.OTP_OUTCOME_SUCCESS,
            null,
            null,
        )
        binding.root
            .findNavController()
            .navigate(
                PersonalIdEmailVerificationForgotBackupCodeFragmentDirections
                    .actionEmailVerificationForgotBackupCodeToSetNewBackupCode(
                        BackupCodeWorkflow.FORGOT_BACKUP_CODE,
                    ),
            )
    }
}
