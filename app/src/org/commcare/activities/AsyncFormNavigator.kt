package org.commcare.activities

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs form-navigation stepping on a background thread and delivers the
 * resulting renderable event back on the main thread.
 */
class AsyncFormNavigator(
    private val lifecycleOwner: LifecycleOwner,
    private val stepWork: StepWork
) {

    fun interface StepWork {
        fun step(): NavResult
    }

    fun interface StartCallback {
        fun onStart()
    }

    fun interface ResultCallback {
        fun onResult(result: NavResult)
    }

    private var job: Job? = null
    private var currentNavId: Long = 0

    fun navigate(resuming: Boolean, onStart: StartCallback, onResult: ResultCallback) {
        val navId = ++currentNavId
        onStart.onStart()
        job = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { stepWork.step() }
            if (navId != currentNavId) {
                return@launch
            }
            onResult.onResult(result)
        }
    }

    fun cancel() {
        currentNavId++
        job?.cancel()
        job = null
    }
}