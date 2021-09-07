package org.commcare.tasks.templates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author $|-|!˅@M
 */
object CoroutineAsyncTaskHelper {
    private val threadFactory = object : ThreadFactory {
        private val mCount = AtomicInteger(1)
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "CoroutinesAsyncTask #${mCount.getAndIncrement()}")
        }
    }
    private val threadPoolExecutor = ThreadPoolExecutor(
            1, // core pool size
            20, // maximum pool size
            3, // keep alive seconds
            TimeUnit.SECONDS,
            SynchronousQueue<Runnable>(),
            threadFactory
    )
    private val serialExecutor = Executors.newSingleThreadExecutor()

    fun serialDispatcher(): CoroutineDispatcher = serialExecutor.asCoroutineDispatcher()

    fun parallelDispatcher(): CoroutineDispatcher = threadPoolExecutor.asCoroutineDispatcher()
}
