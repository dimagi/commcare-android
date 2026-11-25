package org.commcare.views.connect

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.DialogConnectSyncProgressBinding
import org.commcare.views.dialogs.CustomProgressDialog

/**
 * Opportunity Home's sync progress dialog: a fixed title, the task's progress message with a bar
 * and percent, and a Dismiss button that hides the dialog while the sync carries on.
 */
class ConnectSyncProgressDialog : CustomProgressDialog() {
    /** The host told when the user hides the dialog. */
    fun interface Listener {
        fun onSyncProgressDismissed()
    }

    private var dismissEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { dismissEnabled = it.getBoolean(KEY_DISMISS_ENABLED, true) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        val binding = DialogConnectSyncProgressBinding.inflate(LayoutInflater.from(requireContext()))
        binding.dialogTitleText.text = title
        binding.progressDialogMessage.text = message
        binding.progressBarHorizontal.max = progressBarMax
        binding.progressBarHorizontal.progress = progressBarProgress
        binding.progressBarHorizontal.visibility = if (isProgressBarVisible) View.VISIBLE else View.GONE
        bindPercent(binding.syncProgressPercent, progressBarProgress, progressBarMax, isProgressBarVisible)
        binding.syncProgressDismiss.isEnabled = dismissEnabled
        binding.syncProgressDismiss.setOnClickListener {
            (activity as? Listener)?.onSyncProgressDismissed()
            dismiss()
        }
        return AlertDialog
            .Builder(requireContext())
            .setView(binding.root)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    /** The title stays the one the dialog was made with; the task's own titles don't apply. */
    override fun updateTitle(newTitle: String?) {}

    override fun updateProgressBar(
        progress: Int,
        max: Int,
    ) {
        super.updateProgressBar(progress, max)
        dialog?.findViewById<TextView>(R.id.sync_progress_percent)?.let {
            bindPercent(it, progress, max, true)
        }
    }

    override fun updateProgressBarVisibility(visible: Boolean) {
        super.updateProgressBarVisibility(visible)
        dialog?.findViewById<TextView>(R.id.sync_progress_percent)?.let {
            bindPercent(it, progressBarProgress, progressBarMax, visible)
        }
    }

    /** Shows a form send's progress: [message] says how many forms are sent, [percent] how far it is. */
    fun showFormProgress(
        message: String,
        percent: Int,
    ) {
        updateMessage(message)
        updateProgressBar(percent, FULL_PERCENT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_DISMISS_ENABLED, dismissEnabled)
    }

    private fun bindPercent(
        view: TextView,
        progress: Int,
        max: Int,
        barVisible: Boolean,
    ) {
        val showPercent = barVisible && max > 0
        view.visibility = if (showPercent) View.VISIBLE else View.GONE
        if (showPercent) {
            view.text = getString(R.string.connect_sync_dialog_percent, percentOf(progress, max))
        }
    }

    companion object {
        private const val KEY_DISMISS_ENABLED = "dismiss_enabled"
        private const val FULL_PERCENT = 100

        /** A dialog for the sync task [taskId]; [dismissEnabled] is false while forms are sending. */
        @JvmStatic
        fun newInstance(
            title: String,
            message: String,
            taskId: Int,
            dismissEnabled: Boolean,
        ): ConnectSyncProgressDialog =
            ConnectSyncProgressDialog().apply {
                configure(title, message, taskId)
                addProgressBar()
                this.dismissEnabled = dismissEnabled
            }

        fun percentOf(
            progress: Int,
            max: Int,
        ): Int = if (max <= 0) 0 else (progress * 100L / max).toInt().coerceIn(0, 100)
    }
}
