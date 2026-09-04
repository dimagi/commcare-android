package org.commcare.fragments.personalId

import androidx.navigation.findNavController

class PersonalIdEmailVerificationForgotBackupCodeFragment : PersonalIdEmailVerificationFragment() {
    private fun args() = PersonalIdEmailVerificationForgotBackupCodeFragmentArgs.fromBundle(requireArguments())

    override fun resolveEmail(): String = args().email

    override fun resolveWorkflow(): EmailWorkFlow = EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER

    override fun resolveEmailOtpRequestCount(): Int = args().emailOtpRequestCount

    override fun canSkipEmailVerification(): Boolean = false

    override fun onEmailVerified() {
        binding.root
            .findNavController()
            .navigate(
                PersonalIdEmailVerificationForgotBackupCodeFragmentDirections
                    .actionEmailVerificationForgotBackupCodeToSetNewBackupCode(),
            )
    }
}
