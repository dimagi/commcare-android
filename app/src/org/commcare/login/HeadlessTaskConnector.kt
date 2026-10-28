package org.commcare.login

import kotlinx.coroutines.CancellableContinuation
import org.commcare.tasks.templates.CommCareTask
import org.commcare.tasks.templates.CommCareTaskConnector
import kotlin.coroutines.resume

/**
 * No-op [CommCareTaskConnector] that lets the login engine run Activity-bound tasks without an
 * Activity, routing their callbacks to [receiver].
 */
internal class HeadlessTaskConnector<R>(
    private val receiver: R,
) : CommCareTaskConnector<R> {
    override fun <A, B, C> connectTask(task: CommCareTask<A, B, C, R>) = Unit

    override fun startBlockingForTask(id: Int) = Unit

    override fun stopBlockingForTask(id: Int) = Unit

    override fun taskCancelled() = Unit

    override fun getReceiver(): R = receiver

    override fun startTaskTransition() = Unit

    override fun stopTaskTransition(taskId: Int) = Unit

    override fun hideTaskCancelButton() = Unit
}

internal fun <T> CancellableContinuation<T>.resumeOnce(value: T) {
    if (!isCompleted) {
        resume(value)
    }
}
