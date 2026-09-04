package org.commcare.fragments.personalId

/**
 * Launch context for the PersonalID email entry / verification screens.
 *
 *  - [REGISTRATION]: brand-new signup.
 *  - [RECOVERY]: existing user recovering their account after validating the backup code.
 *  - [EXISTING_USER]: a logged-in user adding or verifying their email post-registration.
 *  - [FORGOT_BACKUP_CODE_EXISTING_USER]: profile graph — logged-in user forgot backup code,
 *    verifying via email OTP to be allowed to set a new backup code.
 */
enum class EmailWorkFlow {
    REGISTRATION,
    RECOVERY,
    EXISTING_USER,
    FORGOT_BACKUP_CODE_EXISTING_USER,
}
