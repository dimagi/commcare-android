package org.commcare.views

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
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
        showBar(message, showOffline = false, autoDismiss = true, R.color.connect_warning_color)
    }

    fun showMessage(message: String) {
        showBar(message, showOffline = false, autoDismiss = true, R.color.connect_sky_blue)
    }

    fun showOfflineStatus(message: String) {
        showBar(message, showOffline = true, autoDismiss = false, R.color.connect_warning_color)
    }

    private fun showBar(
        message: String,
        showOffline: Boolean,
        autoDismiss: Boolean,
        @ColorRes color: Int,
    ) {
        barView.setBackgroundColor(barView.context.getColor(color))
        statusBarBinding.tvErrorMessage.text = message

        val offlineVisibility = if (showOffline) View.VISIBLE else View.GONE
        statusBarBinding.ivOffline.visibility = offlineVisibility
        statusBarBinding.tvOfflineLabel.visibility = offlineVisibility

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
