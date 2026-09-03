package org.commcare.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.fragment.app.DialogFragment
import org.commcare.connect.ConnectNavHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.DialogBackupCodeReminderBinding
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

class BackupCodeReminderDialogFragment : DialogFragment() {
    private lateinit var binding: DialogBackupCodeReminderBinding
    private var failedAttempts = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = DialogBackupCodeReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        val user = ConnectUserDatabaseUtil.getUser(requireContext())
        binding.forgotButton.visibility =
            if (user?.email != null) View.VISIBLE else View.GONE
        setupListeners(user?.email)
        reportShown()
    }

    private fun setupListeners(email: String?) {
        binding.backupCodeView.setOnCodeChangedListener { code ->
            binding.confirmButton.isEnabled = code.length == 6
        }
        binding.confirmButton.setOnClickListener { onConfirmClicked() }
        binding.skipButton.setOnClickListener { onSkipClicked() }
        binding.forgotButton.setOnClickListener { onForgotClicked(email) }
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
        val stored = ConnectUserDatabaseUtil.getUser(requireContext())?.pin
        if (entered == stored) {
            reportAttempt(success = true)
            reportOutcome(AnalyticsParamValue.USER_PROMPT_ACTION_ACCEPT)
            PersonalIdReminderHelper.scheduleNext()
            dismiss()
            Toast
                .makeText(
                    requireContext(),
                    R.string.personalid_backup_code_reminder_success_toast,
                    Toast.LENGTH_SHORT,
                ).show()
        } else {
            failedAttempts++
            reportAttempt(success = false)
            if (failedAttempts >= MAX_ATTEMPTS) {
                reportOutcome("max_attempts")
                PersonalIdReminderHelper.scheduleNext()
                dismiss()
                Toast
                    .makeText(
                        requireContext(),
                        R.string.personalid_backup_code_reminder_max_attempts_toast,
                        Toast.LENGTH_LONG,
                    ).show()
            } else {
                showError(MAX_ATTEMPTS - failedAttempts)
            }
        }
    }

    private fun onSkipClicked() {
        reportOutcome(AnalyticsParamValue.USER_PROMPT_ACTION_SKIP)
        PersonalIdReminderHelper.scheduleNext()
        dismiss()
    }

    private fun onForgotClicked(email: String?) {
        reportOutcome(AnalyticsParamValue.USER_PROMPT_ACTION_CANCEL)
        dismiss()
        val activity = requireActivity()
        if (email != null) {
            ConnectNavHelper.launchProfileForBackupCodeRecovery(activity, email)
        } else {
            Toast
                .makeText(
                    activity,
                    R.string.personalid_no_email_forgot_backup_code_toast,
                    Toast.LENGTH_LONG,
                ).show()
            ConnectNavHelper.launchProfile(activity)
        }
    }

    private fun showError(attemptsRemaining: Int) {
        binding.errorBanner.visibility = View.VISIBLE
        binding.errorMessage.text =
            HtmlCompat.fromHtml(
                getString(R.string.personalid_backup_code_reminder_incorrect, attemptsRemaining),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
        binding.backupCodeView.clearCode()
        binding.confirmButton.isEnabled = false
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
        @JvmField
        val TAG: String = BackupCodeReminderDialogFragment::class.java.simpleName

        @JvmStatic
        fun newInstance(): BackupCodeReminderDialogFragment = BackupCodeReminderDialogFragment()

        private const val MAX_ATTEMPTS = 3
    }
}
