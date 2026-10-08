package org.commcare.utils

import org.commcare.fragments.personalId.BackupCodeWorkflow
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.google.services.analytics.AnalyticsParamValue

/**
 * Normalizes the PersonalID backup-code and email journeys into the stable analytics strings
 * emitted with the {@code personalid_account_security_action} event.
 */
object AccountSecurityAnalyticsMapper {
    /**
     * Maps an [EmailWorkFlow] to the `workflow` param, which says which journey the user is in
     * when the shared email and backup-code screens report an event.
     */
    @JvmStatic
    fun workflowParam(workflow: EmailWorkFlow): String =
        when (workflow) {
            EmailWorkFlow.REGISTRATION -> {
                AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_REGISTRATION
            }

            EmailWorkFlow.RECOVERY -> {
                AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_ACCOUNT_RECOVERY
            }

            EmailWorkFlow.EXISTING_USER -> {
                AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_EMAIL_CHANGE
            }

            EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY -> {
                AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_RECOVERY_ACCOUNT_CONFIG
            }

            EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER -> {
                AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_RECOVERY_EXISTING_USER
            }

            EmailWorkFlow.PENDING_BACKUP_CODE -> {
                AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_RECOVERY_PENDING_CODE
            }
        }

    /**
     * Maps a [BackupCodeWorkflow] to the `workflow` param, for the backup-code screens that
     * several journeys share.
     */
    @JvmStatic
    fun workflowParam(workflow: BackupCodeWorkflow): String =
        when (workflow) {
            BackupCodeWorkflow.CHANGE_BACKUP_CODE -> {
                AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_CHANGE_BACKUP_CODE
            }

            BackupCodeWorkflow.EMAIL_CHANGE -> {
                AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_EMAIL_CHANGE_BACKUP_CODE
            }

            BackupCodeWorkflow.FORGOT_BACKUP_CODE -> {
                AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_RECOVERY_EXISTING_USER
            }
        }
}
