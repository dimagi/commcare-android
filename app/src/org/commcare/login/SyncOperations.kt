package org.commcare.login

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApplication
import org.commcare.preferences.ServerUrls
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.PullTaskResultReceiver
import org.commcare.tasks.ResultAndError
import org.commcare.tasks.templates.CommCareTask
import org.commcare.tasks.templates.CommCareTaskConnector
import kotlin.coroutines.resume

internal sealed class SyncOutcome {
    object Success : SyncOutcome()

    data class Failed(
        val error: LoginError,
    ) : SyncOutcome()
}

/**
 * Suspending wrapper around DataPullTask for the normal sync restore path.
 * Emits Syncing progress events with a percentage when DataPullTask reports one.
 *
 * Cancellation is best-effort — AsyncTask cancellation does not preempt running steps.
 */
internal open class SyncOperations(
    private val context: Context,
) {
    open suspend fun pullData(
        username: String,
        password: String,
        sink: LoginProgressSink,
    ): SyncOutcome =
        suspendCancellableCoroutine { continuation ->
            val receiver = NoOpPullTaskResultReceiver()

            val task =
                object : DataPullTask<PullTaskResultReceiver>(
                    username,
                    password,
                    null,
                    ServerUrls.getDataServerKey(),
                    context,
                    CommCareApplication.instance().getDataPullRequester(),
                    false,
                    false,
                    false,
                ) {
                    override fun deliverResult(
                        receiver: PullTaskResultReceiver,
                        result: ResultAndError<PullTaskResult>?,
                    ) {
                        val outcome =
                            when (val pull = result?.data) {
                                PullTaskResult.DOWNLOAD_SUCCESS -> SyncOutcome.Success
                                null -> SyncOutcome.Failed(LoginError.SyncFailed("UNKNOWN", result?.errorMessage))
                                else -> SyncOutcome.Failed(OutcomeMapper.fromPullTaskResult(pull, result.errorMessage))
                            }
                        if (!continuation.isCompleted) continuation.resume(outcome)
                    }

                    override fun deliverUpdate(
                        receiver: PullTaskResultReceiver,
                        vararg update: Int?,
                    ) {
                        val total = update.getOrNull(1)
                        val completed = update.getOrNull(0)
                        val percent =
                            if (total != null && total > 0 && completed != null) {
                                (completed * 100) / total
                            } else {
                                null
                            }
                        sink.onProgress(LoginProgress(LoginPhase.Syncing, percent = percent))
                    }

                    override fun deliverError(
                        receiver: PullTaskResultReceiver,
                        e: Exception?,
                    ) {
                        if (!continuation.isCompleted) {
                            continuation.resume(SyncOutcome.Failed(LoginError.SyncFailed("UNKNOWN", e?.message)))
                        }
                    }
                }

            val connector =
                object : CommCareTaskConnector<PullTaskResultReceiver> {
                    override fun <A, B, C> connectTask(task: CommCareTask<A, B, C, PullTaskResultReceiver>) = Unit

                    override fun startBlockingForTask(id: Int) = Unit

                    override fun stopBlockingForTask(id: Int) = Unit

                    override fun taskCancelled() = Unit

                    override fun getReceiver(): PullTaskResultReceiver = receiver

                    override fun startTaskTransition() = Unit

                    override fun stopTaskTransition(taskId: Int) = Unit

                    override fun hideTaskCancelButton() = Unit
                }

            continuation.invokeOnCancellation { task.cancel(true) }
            task.connect(connector)
            task.executeParallel()
        }
}

internal class NoOpPullTaskResultReceiver : PullTaskResultReceiver {
    override fun handlePullTaskResult(
        resultAndError: ResultAndError<DataPullTask.PullTaskResult>?,
        userTriggeredSync: Boolean,
        formsToSend: Boolean,
        usingRemoteKeyManagement: Boolean,
    ) = Unit

    override fun handlePullTaskUpdate(vararg update: Int?) = Unit

    override fun handlePullTaskError() = Unit
}
