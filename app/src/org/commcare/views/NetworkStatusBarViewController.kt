package org.commcare.views

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.NetworkStatusBarLayoutBinding

class NetworkStatusBarViewController(
    private val statusBarBinding: NetworkStatusBarLayoutBinding,
    private val autoDismissMs: Long = 5000,
) {
    private val barView = statusBarBinding.root
    private val hideRunnable = Runnable { hide() }

    private var animator: ValueAnimator? = null

    fun showError(message: String) {
        showBar(
            message = message,
            color = R.color.connect_warning_color,
            showLeftIcon = true,
            autoDismiss = true,
        )
    }

    fun showMessage(message: String) {
        showBar(
            message = message,
            color = R.color.connect_green,
            showLeftIcon = false,
            autoDismiss = true,
        )
    }

    fun showOfflineStatus(message: String) {
        showBar(
            message = message,
            color = R.color.connect_warning_color,
            showLeftIcon = true,
            autoDismiss = false,
            rightIcon = R.drawable.ic_offline_white,
            rightLabel = R.string.connect_offline,
        )
    }

    fun showBackOnline(message: String) {
        showBar(
            message = message,
            color = R.color.connect_green,
            showLeftIcon = false,
            autoDismiss = true,
            rightIcon = R.drawable.ic_online,
            rightLabel = R.string.connect_back_online,
        )
    }

    private fun showBar(
        message: String,
        @ColorRes color: Int,
        showLeftIcon: Boolean,
        autoDismiss: Boolean,
        @DrawableRes rightIcon: Int? = null,
        @StringRes rightLabel: Int? = null,
    ) {
        barView.setBackgroundColor(barView.context.getColor(color))
        statusBarBinding.tvErrorMessage.text = message

        statusBarBinding.ivError.visibility = if (showLeftIcon) View.VISIBLE else View.GONE

        if (rightIcon != null && rightLabel != null) {
            statusBarBinding.ivOffline.setImageResource(rightIcon)
            statusBarBinding.tvOfflineLabel.setText(rightLabel)
            statusBarBinding.ivOffline.visibility = View.VISIBLE
            statusBarBinding.tvOfflineLabel.visibility = View.VISIBLE

        } else {
            statusBarBinding.ivOffline.visibility = View.GONE
            statusBarBinding.tvOfflineLabel.visibility = View.GONE
        }

        barView.removeCallbacks(hideRunnable)
        cancelAnimator()

        barView.layoutParams.height = 0
        barView.visibility = View.VISIBLE

        barView.post {
            val targetHeight = measureHeight()
            animator =
                barView.animateHeight(
                    from = 0,
                    to = targetHeight,
                ) {
                    barView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    animator = null
                }
        }

        if (autoDismiss) {
            barView.postDelayed(hideRunnable, autoDismissMs)
        }
    }

    fun hide() {
        barView.removeCallbacks(hideRunnable)
        cancelAnimator()

        val initialHeight = barView.height
        if (initialHeight == 0) {
            barView.visibility = View.GONE
            return
        }

        animator =
            barView.animateHeight(
                from = initialHeight,
                to = 0,
            ) {
                barView.visibility = View.GONE
                barView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                animator = null
            }
    }

    private fun cancelAnimator() {
        animator?.cancel()
        animator = null
    }

    private fun measureHeight(): Int {
        barView.measure(
            View.MeasureSpec.makeMeasureSpec(
                (barView.parent as View).width,
                View.MeasureSpec.EXACTLY,
            ),
            View.MeasureSpec.UNSPECIFIED,
        )
        return barView.measuredHeight
    }

    fun cleanup() {
        barView.removeCallbacks(hideRunnable)
        cancelAnimator()
    }
}
