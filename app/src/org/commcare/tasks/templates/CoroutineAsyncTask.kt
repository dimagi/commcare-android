package org.commcare.tasks.templates

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.commcare.CommCareApplication
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author $|-|!˅@M
 */
abstract class CoroutinesAsyncTask<Params, Progress, Result> {

    enum class Status {
        PENDING,
        RUNNING,
        FINISHED
    }

    private var status: Status = Status.PENDING
    private var coroutineJob: Job? = null
    private val _isCancelled = AtomicBoolean(false)
    val isCancelled get() = _isCancelled.get()
    private var executionResult: Result? = null

    @WorkerThread
    protected abstract fun doInBackground(vararg params: Params?): Result

    @MainThread
    protected open fun onProgressUpdate(vararg values: Progress?) {}

    @MainThread
    protected open fun onPostExecute(result: Result?) {}

    @MainThread
    protected open fun onPreExecute() {}

    @MainThread
    protected open fun onCancelled() {}

    fun getStatus() = status

    fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        _isCancelled.set(true)
        coroutineJob?.let { job ->
            return if (job.isCompleted || job.isCancelled) {
                false
            } else {
                if (mayInterruptIfRunning) {
                    job.cancel()
                }
                true
            }
        } ?: kotlin.run {
            return true
        }
    }

    @WorkerThread
    fun publishProgress(vararg progress: Progress) {
        //this is called from background thread to update main thread.
        GlobalScope.launch(Dispatchers.Main) {
            if (!isCancelled) {
                onProgressUpdate(*progress)
            }
        }
    }

    /**
     * Executes asynctasks serially
     */
    @MainThread
    fun execute(vararg params: Params?) {
        execute(CommCareApplication.instance().serialDispatcher(), *params)
    }

    /**
     * Executes asynctasks parallelly
     */
    @MainThread
    fun executeOnExecutor(vararg params: Params?) {
        execute(CommCareApplication.instance().parallelDispatcher(), *params)
    }

    @MainThread
    private fun execute(dispatcher: CoroutineDispatcher, vararg params: Params?) {
        if (status != Status.PENDING) {
            when (status) {
                Status.RUNNING -> throw IllegalStateException("Cannot execute task: the task is already running.")
                Status.FINISHED -> throw IllegalStateException("Cannot execute task:"
                        + " the task has already been executed "
                        + "(a task can be executed only once)")
            }
        }

        status = Status.RUNNING

        onPreExecute()
        GlobalScope.launch(dispatcher) {
            try {
                if (!isCancelled) {
                    runInterruptible {
                        executionResult = doInBackground(*params)
                    }
                }
                if (isCancelled) {
                    throw CancellationException()
                }
            } catch (t: Throwable) {
                _isCancelled.set(true)
            } finally {
                finish()
                status = Status.FINISHED
            }
        }
    }

    private fun finish() {
        GlobalScope.launch(Dispatchers.Main) {
            if (isCancelled) {
                onCancelled()
            } else {
                onPostExecute(executionResult)
            }
        }
    }

}
