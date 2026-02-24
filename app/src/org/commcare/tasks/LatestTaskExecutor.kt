package org.commcare.tasks

import android.os.Handler
import android.os.Looper
import org.javarosa.core.services.Logger
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

/**
 * Executor that ensures only the latest submitted task is executed, and any previous tasks are cancelled.
 * The result is posted back to the main thread via the provided callback.
 */
class LatestTaskExecutor<T> {

    fun interface Callback<T> {
        fun onResult(result: T)
    }

    private val singleThreadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val currentTask: AtomicReference<Future<*>?> = AtomicReference<Future<*>?>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun submit(task: Callable<T>, callback: Callback<T>) {
        val future = singleThreadExecutor.submit {
            try {
                val result = task.call()
                mainHandler.post { callback.onResult(result) }
            } catch (e: Exception) {
                Logger.exception("LatestTaskExecutor task failed", e)
            }
        }
        val previous = currentTask.getAndSet(future)
        previous?.cancel(true)
    }
}
