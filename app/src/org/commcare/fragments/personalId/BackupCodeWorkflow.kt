package org.commcare.fragments.personalId

/**
 * Identifies which backup-code flow is being executed.
 *
 *  - [CONFIRM_BACKUP_CODE_CHANGE_CODE]: user is on the Manage Profile screen and wants to change
 *    their backup code; must confirm the current code first before setting a new one.
 *  - [RECOVERY_FORGOT_BACKUP_CODE]: user has just completed the forgot-backup-code email recovery
 *    flow and is now setting a new backup code.
 */
enum class BackupCodeWorkflow {
    CONFIRM_BACKUP_CODE_CHANGE_CODE,
    RECOVERY_FORGOT_BACKUP_CODE,
}
