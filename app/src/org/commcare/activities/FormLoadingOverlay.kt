package org.commcare.activities

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView

/**
 * Wraps the form-entry loading overlay view. [show] posts a delayed
 * visibility change so fast navigations don't flash the overlay; [hide]
 * cancels any pending show and hides the view immediately.
 */
class FormLoadingOverlay @JvmOverloads constructor(
    private val overlayView: View,
    labelView: TextView,
    labelText: String,
    private val showDelayMillis: Long = DEFAULT_SHOW_DELAY_MILLIS
) {

    private val handler = Handler(Looper.getMainLooper())
    private val showRunnable = Runnable { overlayView.visibility = View.VISIBLE }

    init {
        labelView.text = labelText
    }

    fun show() {
        handler.removeCallbacks(showRunnable)
        handler.postDelayed(showRunnable, showDelayMillis)
    }

    fun hide() {
        handler.removeCallbacks(showRunnable)
        overlayView.visibility = View.GONE
    }

    companion object {
        const val DEFAULT_SHOW_DELAY_MILLIS: Long = 150
    }
}
