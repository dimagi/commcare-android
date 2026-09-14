package org.commcare.personalId

import android.content.Context
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectNavHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.DialogBackupCodeReminderBinding
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.views.dialogs.CustomViewAlertDialog

class BackupCodeReminderDialog private constructor(
    private val binding: DialogBackupCodeReminderBinding,
    private val context: Context,
) : CustomViewAlertDialog(binding.root) {
    private var failedAttempts = 0
    private var outcomeHandled = false
    private val user: ConnectUserRecord? = ConnectUserDatabaseUtil.getUser()

    init {
        makeCancelable()
        binding.forgotButton.visibility = if (user?.email != null) View.VISIBLE else View.GONE
        setupListeners()
        reportShown()
        setOnDismissListener {
            if (!outcomeHandled) {
                PersonalIdReminderHelper.scheduleNext()
            }
        }
    }

    private fun setupListeners() {
        binding.backupCodeView.setOnCodeChangedListener { code ->
            binding.confirmButton.isEnabled = code.length == 6
            if (code.isNotEmpty() && binding.errorBanner.visibility == View.VISIBLE) {
                clearErrorState()
            }
        }
        binding.confirmButton.setOnClickListener { onConfirmClicked() }
        binding.skipButton.setOnClickListener { onSkipClicked() }
        binding.forgotButton.setOnClickListener { onForgotClicked() }
        binding.backupCodeVisibilityToggle.setOnClickListener {
            toggleVisibility(binding.backupCodeVisibilityToggle)
        }
    }

    private fun toggleVisibility(toggle: ImageView) {
        binding.backupCodeView.isPasswordVisible = !binding.backupCodeView.isPasswordVisible
        toggle.setImageResource(
            if (binding.backupCodeView.isPasswordVisible) {
                R.drawable.ic_visibility_off_24
            } else {
                R.drawable.ic_visibility_24
            },
        )
    }

    private fun onConfirmClicked() {
        val entered = binding.backupCodeView.codeValue
        val stored =
            user?.pin ?: run {
                dismiss()
                return
            }
        if (entered == stored) {
            reportAttempt(success = true)
            reportOutcome(AnalyticsParamValue.USER_PROMPT_ACTION_ACCEPT)
            outcomeHandled = true
            PersonalIdReminderHelper.scheduleNext()
            dismiss()
            Toast.makeText(context, R.string.personalid_backup_code_reminder_success_toast, Toast.LENGTH_SHORT).show()
        } else {
            failedAttempts++
            reportAttempt(success = false)
            if (failedAttempts >= MAX_ATTEMPTS) {
                reportOutcome("max_attempts")
                outcomeHandled = true
                PersonalIdReminderHelper.scheduleNext()
                dismiss()
                Toast.makeText(context, R.string.personalid_backup_code_reminder_max_attempts_toast, Toast.LENGTH_LONG).show()
            } else {
                showError(MAX_ATTEMPTS - failedAttempts)
            }
        }
    }

    private fun onSkipClicked() {
        reportOutcome(AnalyticsParamValue.USER_PROMPT_ACTION_SKIP)
        outcomeHandled = true
        PersonalIdReminderHelper.scheduleNext()
        dismiss()
    }

    private fun onForgotClicked() {
        reportOutcome(AnalyticsParamValue.USER_PROMPT_ACTION_CANCEL)
        outcomeHandled = true
        dismiss()
        ConnectNavHelper.launchProfileForBackupCodeRecovery(context)
    }

    private fun showError(attemptsRemaining: Int) {
        binding.errorBanner.visibility = View.VISIBLE
        binding.errorMessage.text =
            HtmlCompat.fromHtml(
                context.getString(R.string.personalid_backup_code_reminder_incorrect, attemptsRemaining),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
        binding.backupCodeView.clearCode()
        binding.backupCodeView.setErrorState(true)
        binding.lockIconContainer.setBackgroundResource(R.drawable.connect_side_icon_error_bg)
        binding.lockIcon.setColorFilter(
            ContextCompat.getColor(context, R.color.connect_red),
            PorterDuff.Mode.SRC_IN,
        )
        binding.confirmButton.isEnabled = false
    }

    private fun clearErrorState() {
        binding.errorBanner.visibility = View.GONE
        binding.lockIconContainer.setBackgroundResource(R.drawable.connect_side_icon_bg)
        binding.lockIcon.clearColorFilter()
    }

    private fun reportShown() {
        FirebaseAnalyticsUtil.reportUserPromptEvent(
            AnalyticsParamValue.USER_PROMPT_TYPE_BACKUP_CODE_REMINDER,
            "shown",
            "",
        )
    }

    private fun reportAttempt(success: Boolean) {
        FirebaseAnalyticsUtil.reportUserPromptEvent(
            AnalyticsParamValue.USER_PROMPT_TYPE_BACKUP_CODE_REMINDER,
            if (success) "attempt_success" else "attempt_failed",
            failedAttempts.toString(),
        )
    }

    private fun reportOutcome(action: String) {
        FirebaseAnalyticsUtil.reportUserPromptEvent(
            AnalyticsParamValue.USER_PROMPT_TYPE_BACKUP_CODE_REMINDER,
            action,
            "",
        )
    }

    companion object {
        private const val MAX_ATTEMPTS = 3

        @JvmStatic
        fun show(activity: CommCareActivity<*>) {
            val binding = DialogBackupCodeReminderBinding.inflate(LayoutInflater.from(activity))
            activity.showAlertDialog(BackupCodeReminderDialog(binding, activity))
        }
    }
}
