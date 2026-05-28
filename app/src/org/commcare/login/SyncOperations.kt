package org.commcare.login

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApplication
import org.commcare.preferences.ServerUrls
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.PullTaskResultReceiver
import org.commcare.tasks.ResultAndError

internal sealed class SyncOutcome {
    object Success : SyncOutcome()

    data class Failed(
        val error: LoginError,
    ) : SyncOutcome()
}

internal open class SyncOperations(
    private val context: Context,
) {
    open suspend fun pullData(
        username: String,
        password: String,
        sink: LoginProgressSink,
    ): SyncOutcome =
        suspendCancellableCoroutine { continuation ->
            sink.onProgress(LoginProgress(LoginPhase.Syncing))
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
                                null ->
                                    SyncOutcome.Failed(
                                        LoginError.SyncFailed(
                                            SyncFailureReason.UNKNOWN,
                                            result?.errorMessage,
                                        ),
                                    )
                                else ->
                                    SyncOutcome.Failed(
                                        OutcomeMapper.fromPullTaskResult(
                                            pull,
                                            result.errorMessage,
                                        ),
                                    )
                            }
                        continuation.resumeOnce(outcome)
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
                        sink.onProgress(
                            LoginProgress(LoginPhase.Syncing, percent = percent),
                        )
                    }

                    override fun deliverError(
                        receiver: PullTaskResultReceiver,
                        e: Exception?,
                    ) {
                        if (e is CancellationException) {
                            throw e
                        }

                        continuation.resumeOnce(
                            SyncOutcome.Failed(
                                LoginError.SyncFailed(
                                    SyncFailureReason.UNKNOWN,
                                    e?.message,
                                ),
                            ),
                        )
                    }
                }

            continuation.invokeOnCancellation { task.cancel(true) }
            task.connect(HeadlessTaskConnector(receiver))
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
