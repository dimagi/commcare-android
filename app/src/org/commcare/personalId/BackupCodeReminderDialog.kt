package org.commcare.personalId

import android.content.Context
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectNavHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.DialogBackupCodeReminderBinding
import org.commcare.fragments.personalId.BasePersonalIdBackupCodeFragment.Companion.BACKUP_CODE_LENGTH
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.views.connect.bindVisibilityToggle
import org.commcare.views.dialogs.CustomViewAlertDialog
import org.commcare.views.dialogs.StandardAlertDialog

class BackupCodeReminderDialog private constructor(
    private val binding: DialogBackupCodeReminderBinding,
    private val context: Context,
    private val user: ConnectUserRecord,
) : CustomViewAlertDialog(binding.root) {
    private var failedAttempts = 0

    init {
        makeCancelable()
        setupListeners()
    }

    private fun setupListeners() {
        binding.backupCodeView.setOnCodeChangedListener { onCodeChanged(it) }
        binding.confirmButton.setOnClickListener {
            reportReminderPrompt(AnalyticsParamValue.USER_PROMPT_ACTION_ACCEPT)
            onConfirmClicked()
        }
        binding.skipButton.setOnClickListener {
            reportReminderPrompt(AnalyticsParamValue.USER_PROMPT_ACTION_SKIP)
            onSkipClicked()
        }
        binding.forgotButton.setOnClickListener {
            reportReminderPrompt(AnalyticsParamValue.USER_PROMPT_ACTION_FORGOT)
            onForgotClicked()
        }
        binding.backupCodeView.bindVisibilityToggle(binding.backupCodeVisibilityToggle)
    }

    private fun onCodeChanged(code: String) {
        binding.confirmButton.isEnabled = code.length == BACKUP_CODE_LENGTH
        if (code.isNotEmpty() && binding.errorBanner.isVisible) {
            clearErrorState()
        }
    }

    private fun onConfirmClicked() {
        val entered = binding.backupCodeView.codeValue
        if (entered == user.pin) {
            reportConfirmAttempt(AnalyticsParamValue.OTP_OUTCOME_SUCCESS, null)
            dismiss()
            Toast.makeText(context, R.string.personalid_backup_code_reminder_success_toast, Toast.LENGTH_SHORT).show()
        } else {
            failedAttempts++
            reportConfirmAttempt(AnalyticsParamValue.OTP_OUTCOME_FAILURE, failedAttempts)
            if (failedAttempts >= MAX_ATTEMPTS) {
                reportReminderEvent(
                    AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_CONFIRM_CODE_MAX_ATTEMPTS,
                    null,
                    failedAttempts,
                )
                Toast.makeText(context, R.string.personalid_backup_code_reminder_max_attempts_toast, Toast.LENGTH_LONG).show()
                onForgotClicked()
            } else {
                showError(MAX_ATTEMPTS - failedAttempts)
            }
        }
    }

    private fun onSkipClicked() {
        dismiss()
    }

    private fun onForgotClicked() {
        dismiss()
        ConnectNavHelper.goToProfile(context, initiateBackupCodeRecovery = true)
    }

    private fun reportReminderPrompt(action: String) {
        reportPrompt(action, AnalyticsParamValue.USER_PROMPT_INFO_BACKUP_CODE_REMINDER)
    }

    private fun reportConfirmAttempt(
        outcome: String,
        failedAttempts: Int?,
    ) {
        reportReminderEvent(
            AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_CONFIRM_CODE_ATTEMPT,
            outcome,
            failedAttempts,
        )
    }

    private fun reportReminderEvent(
        eventType: String,
        outcome: String?,
        failedAttempts: Int?,
    ) {
        FirebaseAnalyticsUtil.reportPersonalIdAccountSecurityAction(
            AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_BACKUP_CODE_REMINDER,
            eventType,
            outcome,
            null,
            failedAttempts,
        )
    }

    private fun showError(attemptsRemaining: Int) {
        binding.errorBanner.visibility = View.VISIBLE
        binding.errorMessage.text = context.getString(R.string.personalid_backup_code_reminder_incorrect, attemptsRemaining)
        binding.backupCodeView.clearCode()
        binding.backupCodeView.setErrorState(true)
        binding.lockIconContainer.isActivated = true
        binding.lockIcon.setColorFilter(
            ContextCompat.getColor(context, R.color.connect_red),
            PorterDuff.Mode.SRC_IN,
        )
        binding.confirmButton.isEnabled = false
    }

    private fun clearErrorState() {
        binding.errorBanner.visibility = View.GONE
        binding.lockIconContainer.isActivated = false
        binding.lockIcon.clearColorFilter()
    }

    companion object {
        private const val MAX_ATTEMPTS = 3

        @JvmStatic
        fun show(activity: CommCareActivity<*>) {
            val user = ConnectUserDatabaseUtil.getUser()
            if (user.pin == null) {
                showSetBackupCodePrompt(activity)
            } else {
                val binding = DialogBackupCodeReminderBinding.inflate(LayoutInflater.from(activity))
                reportPrompt(
                    AnalyticsParamValue.USER_PROMPT_ACTION_SHOWN,
                    AnalyticsParamValue.USER_PROMPT_INFO_BACKUP_CODE_REMINDER,
                )
                activity.showAlertDialog(BackupCodeReminderDialog(binding, activity, user))
            }
        }

        private fun showSetBackupCodePrompt(activity: CommCareActivity<*>) {
            val dialog =
                StandardAlertDialog(
                    activity.getString(R.string.personalid_set_backup_code_reminder_title),
                    activity.getString(R.string.personalid_set_backup_code_reminder_message),
                )
            dialog.setPositiveButton(activity.getString(R.string.personalid_backup_code_reminder_confirm)) { _, _ ->
                reportSetBackupCodePrompt(AnalyticsParamValue.USER_PROMPT_ACTION_ACCEPT)
                activity.dismissAlertDialog()
                ConnectNavHelper.goToProfileForPendingBackupCode(activity)
            }
            dialog.setNegativeButton(activity.getString(R.string.personalid_backup_code_reminder_skip)) { _, _ ->
                reportSetBackupCodePrompt(AnalyticsParamValue.USER_PROMPT_ACTION_SKIP)
                activity.dismissAlertDialog()
            }
            reportSetBackupCodePrompt(AnalyticsParamValue.USER_PROMPT_ACTION_SHOWN)
            activity.showAlertDialog(dialog)
        }

        private fun reportSetBackupCodePrompt(action: String) {
            reportPrompt(action, AnalyticsParamValue.USER_PROMPT_INFO_SET_BACKUP_CODE_REMINDER)
        }

        private fun reportPrompt(
            action: String,
            info: String,
        ) {
            FirebaseAnalyticsUtil.reportUserPromptEvent(
                AnalyticsParamValue.USER_PROMPT_TYPE_BACKUP_CODE,
                action,
                info,
            )
        }
    }
}
