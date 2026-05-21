package org.commcare.fragments.personalId

/**
 * Launch context for the PersonalID email entry / verification screens.
 *
 *  - [REGISTRATION]: brand-new signup — the user is going through the full PersonalID flow
 *    (phone → biometric → OTP → name → backup code → email → photo).
 *  - [RECOVERY]: existing user recovering their account on a new device after validating the
 *    backup code. On skip / successful OTP, the email step finalizes account recovery instead
 *    of moving to photo capture.
 *  - [EXISTING_USER]: a legacy logged-in user adding (or verifying) their email
 *    post-registration. No upstream PersonalID session data is populated on this entry path.
 */
enum class EmailWorkFlow {
    REGISTRATION,
    RECOVERY,
    EXISTING_USER,
}
