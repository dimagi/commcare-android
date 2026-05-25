package org.commcare.login

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApplication
import org.commcare.network.LocalReferencePullResponseFactory
import org.commcare.suite.model.OfflineUserRestore
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.PullTaskResultReceiver
import org.commcare.tasks.ResultAndError
import org.commcare.tasks.templates.CommCareTask
import org.commcare.tasks.templates.CommCareTaskConnector
import kotlin.coroutines.resume

/**
 * Demo-user login short-circuit. Skips remote key-record management and pulls a
 * pre-bundled restore from the demo CCZ via LocalReferencePullResponseFactory.
 *
 * Mirrors FormAndDataSyncer.performDemoUserRestore verbatim.
 */
internal open class DemoLoginPath(
    private val context: Context,
) {
    open suspend fun login(sink: LoginProgressSink): SyncOutcome {
        val demoRestore: OfflineUserRestore =
            CommCareApplication
                .instance()
                .getCommCarePlatform()
                .demoUserRestore
                ?: return SyncOutcome.Failed(LoginError.SyncFailed("DEMO_RESTORE_MISSING", null))

        // Side-effect required by LocalReferencePullResponseFactory before the task runs.
        LocalReferencePullResponseFactory.setRequestPayloads(arrayOf(demoRestore.reference))

        return suspendCancellableCoroutine { cont ->
            val receiver = NoOpPullTaskResultReceiver()

            val task =
                object : DataPullTask<PullTaskResultReceiver>(
                    demoRestore.username,
                    OfflineUserRestore.DEMO_USER_PASSWORD,
                    "demo_id",
                    "fake-server-that-is-never-used",
                    context,
                    LocalReferencePullResponseFactory.INSTANCE,
                    // blockRemoteKeyManagement =
                    true,
                    // skipFixtures =
                    false,
                    // userTriggeredSync =
                    false,
                ) {
                    override fun deliverResult(
                        receiver: PullTaskResultReceiver,
                        result: ResultAndError<PullTaskResult>?,
                    ) {
                        val outcome =
                            when (val pull = result?.data) {
                                PullTaskResult.DOWNLOAD_SUCCESS -> SyncOutcome.Success
                                null -> SyncOutcome.Failed(LoginError.SyncFailed("UNKNOWN", null))
                                else -> SyncOutcome.Failed(OutcomeMapper.fromPullTaskResult(pull, result.errorMessage))
                            }
                        if (!cont.isCompleted) cont.resume(outcome)
                    }

                    override fun deliverUpdate(
                        receiver: PullTaskResultReceiver,
                        vararg update: Int?,
                    ) {
                        sink.onProgress(LoginProgress(LoginPhase.Syncing))
                    }

                    override fun deliverError(
                        receiver: PullTaskResultReceiver,
                        e: Exception?,
                    ) {
                        if (!cont.isCompleted) {
                            cont.resume(SyncOutcome.Failed(LoginError.SyncFailed("UNKNOWN", e?.message)))
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

            cont.invokeOnCancellation { task.cancel(true) }
            task.connect(connector)
            task.executeParallel()
        }
    }
}
