package org.commcare.views.connect

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.MaterialColors
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.DialogConnectSyncResultBinding

/**
 * How Opportunity Home's sync ended: complete, with the forms it sent, or failed, with the error
 * behind a Details button.
 */
class ConnectSyncResultDialog : DialogFragment() {
    private var showingDetails = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        showingDetails = savedInstanceState?.getBoolean(KEY_SHOWING_DETAILS) ?: false
        val args = requireArguments()
        val binding = DialogConnectSyncResultBinding.inflate(LayoutInflater.from(requireContext()))
        if (args.getBoolean(ARG_SUCCESS)) {
            bindComplete(binding, args.getInt(ARG_FORMS_SENT))
        } else {
            bindFailed(binding, args.getString(ARG_DETAILS).orEmpty())
        }
        binding.syncResultDismiss.setOnClickListener { dismiss() }
        return AlertDialog
            .Builder(requireContext())
            .setView(binding.root)
            .create()
            .apply { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SHOWING_DETAILS, showingDetails)
    }

    private fun bindComplete(
        binding: DialogConnectSyncResultBinding,
        formsSent: Int,
    ) {
        binding.syncResultIcon.setImageResource(R.drawable.ic_connect_delivery_check_circle)
        tintIcon(binding, R.attr.connectStatusPositive)
        binding.syncResultTitle.setText(R.string.connect_sync_dialog_complete_title)
        binding.syncResultMessage.text =
            resources.getQuantityString(R.plurals.connect_sync_dialog_complete_message, formsSent, formsSent)
    }

    private fun bindFailed(
        binding: DialogConnectSyncResultBinding,
        details: String,
    ) {
        binding.syncResultIcon.setImageResource(R.drawable.ic_alert_circle_24)
        tintIcon(binding, R.attr.connectStatusNegative)
        binding.syncResultTitle.setText(R.string.connect_sync_dialog_failed_title)
        binding.syncResultTitle.setTextColor(MaterialColors.getColor(binding.root, R.attr.connectStatusNegative))
        binding.syncResultMessage.setText(R.string.connect_sync_dialog_failed_message)
        if (details.isBlank()) {
            return
        }
        binding.syncResultDetails.text = details
        binding.syncResultDetailsButton.visibility = View.VISIBLE
        binding.syncResultDetailsButton.setOnClickListener {
            showingDetails = !showingDetails
            showDetails(binding)
        }
        showDetails(binding)
    }

    private fun showDetails(binding: DialogConnectSyncResultBinding) {
        binding.syncResultDetails.visibility = if (showingDetails) View.VISIBLE else View.GONE
    }

    private fun tintIcon(
        binding: DialogConnectSyncResultBinding,
        colorAttr: Int,
    ) {
        ImageViewCompat.setImageTintList(
            binding.syncResultIcon,
            ColorStateList.valueOf(MaterialColors.getColor(binding.root, colorAttr)),
        )
    }

    companion object {
        const val TAG = "connect_sync_result_dialog"
        private const val ARG_SUCCESS = "success"
        private const val ARG_FORMS_SENT = "forms_sent"
        private const val ARG_DETAILS = "details"
        private const val KEY_SHOWING_DETAILS = "showing_details"

        /** The dialog for a sync that sent [formsSent] forms and pulled. */
        @JvmStatic
        fun complete(formsSent: Int): ConnectSyncResultDialog =
            ConnectSyncResultDialog().apply {
                arguments = bundleOf(ARG_SUCCESS to true, ARG_FORMS_SENT to formsSent)
            }

        /** The dialog for a sync that failed with [details]. */
        @JvmStatic
        fun failed(details: String): ConnectSyncResultDialog =
            ConnectSyncResultDialog().apply {
                arguments = bundleOf(ARG_SUCCESS to false, ARG_DETAILS to details)
            }

        /** Shows [dialog] in place of any result still on screen; false when the state is already saved. */
        @JvmStatic
        fun show(
            fragmentManager: FragmentManager,
            dialog: ConnectSyncResultDialog,
        ): Boolean {
            if (fragmentManager.isStateSaved) {
                return false
            }
            (fragmentManager.findFragmentByTag(TAG) as? DialogFragment)?.dismiss()
            dialog.show(fragmentManager, TAG)
            return true
        }
    }
}
