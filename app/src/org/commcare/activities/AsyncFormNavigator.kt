package org.commcare.activities

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs form-navigation stepping on a background thread and delivers the
 * resulting renderable event back on the main thread.
 *
 * Re-entry while a navigation is already in flight is a no-op.
 */
class AsyncFormNavigator(
    private val lifecycleOwner: LifecycleOwner,
    private val stepWork: StepWork,
    private val overlayCallback: OverlayCallback
) {

    fun interface StepWork {
        fun step(): NavResult
    }

    fun interface OverlayCallback {
        fun setVisible(visible: Boolean)
    }

    fun interface ResultCallback {
        fun onResult(result: NavResult)
    }

    private var navigationInFlight = false

    fun isNavigationInFlight(): Boolean = navigationInFlight

    fun navigate(onComplete: ResultCallback) {
        if (navigationInFlight) {
            return
        }
        navigationInFlight = true
        overlayCallback.setVisible(true)
        lifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { stepWork.step() }
                onComplete.onResult(result)
            } finally {
                navigationInFlight = false
                overlayCallback.setVisible(false)
            }
        }
    }
}