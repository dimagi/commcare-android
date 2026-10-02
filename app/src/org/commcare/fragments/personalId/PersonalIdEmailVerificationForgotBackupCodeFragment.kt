package org.commcare.fragments.personalId

import androidx.navigation.findNavController

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
        binding.root
            .findNavController()
            .navigate(
                PersonalIdEmailVerificationForgotBackupCodeFragmentDirections
                    .actionEmailVerificationForgotBackupCodeToSetNewBackupCode(),
            )
    }
}
