package org.commcare.fragments.personalId

/**
 * Why the user is on a backup-code screen. Passed as a nav argument so the confirm and
 * set-new-code screens can report which journey they belong to, which their other arguments
 * cannot tell them.
 *
 *  - [CHANGE_BACKUP_CODE]: user chose to change their backup code from Manage Profile.
 *  - [EMAIL_CHANGE]: user is changing their email and must confirm the current code first.
 *  - [FORGOT_BACKUP_CODE]: user forgot their code and recovered via email OTP.
 */
enum class BackupCodeWorkflow {
    CHANGE_BACKUP_CODE,
    EMAIL_CHANGE,
    FORGOT_BACKUP_CODE,
}
