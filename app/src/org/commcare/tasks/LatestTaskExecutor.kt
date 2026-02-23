package org.commcare.tasks

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

class LatestTaskExecutor {
    var singleThreadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    var currentTask: AtomicReference<Future<*>?> = AtomicReference<Future<*>?>()

    fun <T> submit(task: Callable<T?>): CompletableFuture<T?> {
        val previous = currentTask.getAndSet(null)
        previous?.cancel(true)

        val completable = CompletableFuture<T?>()

        val future = singleThreadExecutor.submit(Runnable {
            try {
                completable.complete(task.call())
            } catch (e: Exception) {
                completable.completeExceptionally(e)
            }
        })

        currentTask.set(future)
        return completable
    }
}