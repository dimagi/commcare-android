package org.commcare.utils

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import org.commcare.dalvik.databinding.InlineErrorLayoutBinding

class ErrorViewController(
    private val errorBinding: InlineErrorLayoutBinding,
    private val autoDismissMs: Long = 5000
) {
    private val errorView = errorBinding.root
    private val hideRunnable = Runnable { hide() }

    private var animator: ValueAnimator? = null

    fun show(message: String) {
        errorBinding.tvErrorMessage.text = message
        errorView.removeCallbacks(hideRunnable)

        cancelAnimator()

        val targetHeight = measureHeight()

        errorView.layoutParams.height = 0
        errorView.visibility = View.VISIBLE

        animator = errorView.animateHeight(
            from = 0,
            to = targetHeight
        ) {
            errorView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            animator = null
        }

        errorView.postDelayed(hideRunnable, autoDismissMs)
    }

    fun hide() {
        errorView.removeCallbacks(hideRunnable)
        cancelAnimator()

        val initialHeight = errorView.height
        if (initialHeight == 0) {
            errorView.visibility = View.GONE
            return
        }

        animator = errorView.animateHeight(
            from = initialHeight,
            to = 0
        ) {
            errorView.visibility = View.GONE
            errorView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            animator = null
        }
    }

    private fun cancelAnimator() {
        animator?.cancel()
        animator = null
    }

    private fun measureHeight(): Int {
        errorView.measure(
            View.MeasureSpec.makeMeasureSpec(
                (errorView.parent as View).width,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.UNSPECIFIED
        )
        return errorView.measuredHeight
    }
}