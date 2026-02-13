package org.commcare.views.connect

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.commcare.dalvik.R

/**
 * Helper class for creating and displaying custom standardized Material alert dialogs for Connect.
 *
 * @author Conroy Ricketts
 */
class ConnectDialogCreationHelper {
    companion object {
        /**
         * Displays a standardized custom Connect dialog with configurable text, colors, and
         * actions.
         *
         * @param context The context used to inflate the layout and create the dialog.
         * @param titleText The text to display as the dialog title.
         * @param messageText The text to display as the dialog message body.
         * @param negativeButtonText The text for the negative action button.
         * @param negativeButtonCallback The callback to execute when the negative button is clicked
         * and after the dialog is dismissed.
         * @param negativeButtonTextColorRes The color resource for the negative button text.
         * @param negativeButtonBackgroundColorRes The color resource for the negative button
         * background.
         * @param positiveButtonText The text for the positive action button.
         * @param positiveButtonCallback The callback to execute when the positive button is clicked
         * and after the dialog is dismissed.
         * @param positiveButtonTextColorRes The color resource for the positive button text.
         * @param positiveButtonBackgroundColorRes The color resource for the positive button
         * background.
         */
        fun showCustomConnectDialog(
            context: Context,
            titleText: String,
            messageText: String,
            negativeButtonText: String,
            negativeButtonCallback: () -> Unit,
            @ColorRes negativeButtonTextColorRes: Int,
            @ColorRes negativeButtonBackgroundColorRes: Int,
            positiveButtonText: String,
            positiveButtonCallback: () -> Unit,
            @ColorRes positiveButtonTextColorRes: Int,
            @ColorRes positiveButtonBackgroundColorRes: Int,
        ) {
            val dialogView =
                LayoutInflater
                    .from(context)
                    .inflate(R.layout.connect_custom_dialog, null)
            val dialog =
                MaterialAlertDialogBuilder(context)
                    .setView(dialogView)
                    .create()

            // Make the window background transparent or else it will be visible behind the rounded
            // corners.
            dialog.window!!.setBackgroundDrawable(
                ContextCompat.getColor(context, R.color.transparent).toDrawable(),
            )

            // Set the title.
            dialogView.findViewById<TextView>(R.id.tv_dialog_title).apply {
                text = titleText
            }

            // Set the message body.
            dialogView.findViewById<TextView>(R.id.tv_dialog_message).apply {
                text = messageText
            }

            // Set the close (X) button.
            dialogView.findViewById<ImageView>(R.id.iv_dialog_close).apply {
                setOnClickListener { dialog.dismiss() }
            }

            // Set the negative button.
            dialogView.findViewById<MaterialButton>(R.id.mb_negative_button).apply {
                text = negativeButtonText
                setTextColor(ContextCompat.getColor(context, negativeButtonTextColorRes))
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, negativeButtonBackgroundColorRes),
                )
                setOnClickListener {
                    dialog.dismiss()
                    negativeButtonCallback()
                }
            }

            // Set the positive button.
            dialogView.findViewById<MaterialButton>(R.id.mb_positive_button).apply {
                text = positiveButtonText
                setTextColor(ContextCompat.getColor(context, positiveButtonTextColorRes))
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, positiveButtonBackgroundColorRes),
                )
                setOnClickListener {
                    dialog.dismiss()
                    positiveButtonCallback()
                }
            }

            dialog.show()
        }
    }
}
