package org.commcare.views.dialogs

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.CustomThreeButtonAlertDialogBinding

/**
 * Custom class for creating custom three-button alert dialogs. The three buttons include an "X"
 * button in the top right corner in addition to the positive and negative buttons.
 *
 * @param titleText The text to display as the dialog title.
 * @param messageText The text to display as the dialog message body.
 * @param negativeButtonText The text for the negative action button.
 * @param negativeButtonCallback The callback to execute when the negative button is clicked and
 * after the dialog is dismissed.
 * @param negativeButtonTextColorRes The color resource for the negative button text.
 * @param negativeButtonBackgroundColorRes The color resource for the negative button background.
 * @param positiveButtonText The text for the positive action button.
 * @param positiveButtonCallback The callback to execute when the positive button is clicked and
 * after the dialog is dismissed.
 * @param positiveButtonTextColorRes The color resource for the positive button text.
 * @param positiveButtonBackgroundColorRes The color resource for the positive button background.
 *
 * @author Conroy Ricketts
 */
class CustomThreeButtonAlertDialog(
    private val titleText: String,
    private val messageText: String,
    private val negativeButtonText: String,
    private val negativeButtonCallback: () -> Unit,
    @ColorRes private val negativeButtonTextColorRes: Int,
    @ColorRes private val negativeButtonBackgroundColorRes: Int,
    private val positiveButtonText: String,
    private val positiveButtonCallback: () -> Unit,
    @ColorRes private val positiveButtonTextColorRes: Int,
    @ColorRes private val positiveButtonBackgroundColorRes: Int,
) : CommCareAlertDialog() {
    override fun initView(context: Context) {
        val inflater = LayoutInflater.from(context)
        val binding = CustomThreeButtonAlertDialogBinding.inflate(inflater)
        setView(binding.root)

        // Set the title.
        binding.tvDialogTitle.text = titleText

        // Set the message body.
        binding.tvDialogMessage.text = messageText

        // Set the close (X) button.
        binding.ivDialogClose.apply {
            setOnClickListener { dismiss() }
        }

        // Set the negative button.
        binding.mbNegativeButton.apply {
            text = negativeButtonText
            setTextColor(ContextCompat.getColor(context, negativeButtonTextColorRes))
            backgroundTintList =
                ColorStateList.valueOf(
                    ContextCompat.getColor(context, negativeButtonBackgroundColorRes),
                )
            setOnClickListener {
                dismiss()
                negativeButtonCallback()
            }
        }

        // Set the positive button.
        binding.mbPositiveButton.apply {
            text = positiveButtonText
            setTextColor(ContextCompat.getColor(context, positiveButtonTextColorRes))
            backgroundTintList =
                ColorStateList.valueOf(
                    ContextCompat.getColor(context, positiveButtonBackgroundColorRes),
                )
            setOnClickListener {
                dismiss()
                positiveButtonCallback()
            }
        }
    }

    fun showDialog(context: Context) {
        showNonPersistentDialog(context)

        // Make the window background transparent after the dialog is built or else the square
        // background will be visible behind the rounded corners.
        dialog.window!!.setBackgroundDrawableResource(R.color.transparent)
    }
}
