package org.commcare.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import kotlin.coroutines.cancellation.CancellationException

/**
 * Coroutine-based task executor that ensures only the latest submitted task is executed, and any previous tasks
 * are cancelled. Both result and error are delivered to the main thread via the provided callback.
 */
class LatestTaskExecutor<T> {
    interface Callback<T> {
        fun onResult(result: T)
        fun onError(exception: Exception)
    }

    private var currentJob: Job? = null

    fun submit(
        scope: CoroutineScope,
        task: Callable<T>,
        callback: Callback<T>,
    ) {
        currentJob?.cancel()
        currentJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { task.call() }
                withContext(Dispatchers.Main) { callback.onResult(result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback.onError(e) }
            }
        }
    }
}
