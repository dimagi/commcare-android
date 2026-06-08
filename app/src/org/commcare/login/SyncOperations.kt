package org.commcare.login

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApplication
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.engine.resource.installers.SingleAppInstallation
import org.commcare.network.DataPullRequester
import org.commcare.network.LocalReferencePullResponseFactory
import org.commcare.preferences.ServerUrls
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.PullTaskResultReceiver
import org.commcare.tasks.ResultAndError
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.services.Logger

internal sealed class SyncOutcome {
    object Success : SyncOutcome()

    data class Failed(
        val error: LoginError,
    ) : SyncOutcome()
}

/** Per-[DataPullMode] data-pull configuration: OTA against the data server vs a bundled-reference local restore. */
internal data class PullPlan(
    val server: String,
    val userId: String?,
    val requester: DataPullRequester,
    val blockRemoteKeyManagement: Boolean,
    val payloadReferences: List<String>,
)

/**
 * Suspending wrapper around [DataPullTask]: resolves a [PullPlan] from the request's [DataPullMode],
 * runs the pull off the UI thread, and reduces it to a single [SyncOutcome].
 */
internal open class SyncOperations(
    private val context: Context,
) {
    open suspend fun pullData(
        username: String,
        password: String,
        mode: DataPullMode,
        listener: LoginProgressListener,
    ): SyncOutcome =
        suspendCancellableCoroutine { continuation ->
            listener.onProgress(LoginProgress(LoginPhase.Syncing))

            if (mode == DataPullMode.CONSUMER_APP && !localRestoreReferenceExists()) {
                continuation.resumeOnce(
                    SyncOutcome.Failed(LoginError.UnknownFailure("Local restore file missing")),
                )
                return@suspendCancellableCoroutine
            }

            val plan = resolvePullPlan(mode)
            if (plan.payloadReferences.isNotEmpty()) {
                LocalReferencePullResponseFactory.setRequestPayloads(plan.payloadReferences.toTypedArray())
            }

            val receiver = NoOpPullTaskResultReceiver()

            val task =
                object : DataPullTask<PullTaskResultReceiver>(
                    username,
                    password,
                    plan.userId,
                    plan.server,
                    context,
                    plan.requester,
                    plan.blockRemoteKeyManagement,
                    false,
                    false,
                ) {
                    override fun deliverResult(
                        receiver: PullTaskResultReceiver,
                        result: ResultAndError<PullTaskResult>?,
                    ) {
                        val outcome =
                            when (val pull = result?.data) {
                                PullTaskResult.DOWNLOAD_SUCCESS -> {
                                    SyncOutcome.Success
                                }

                                null -> {
                                    SyncOutcome.Failed(
                                        LoginError.UnknownFailure(result?.errorMessage),
                                    )
                                }

                                else -> {
                                    SyncOutcome.Failed(
                                        OutcomeMapper.fromPullTaskResult(
                                            pull,
                                            result.errorMessage,
                                        ),
                                    )
                                }
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
                        listener.onProgress(
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
                                LoginError.UnknownFailure(e?.message),
                            ),
                        )
                    }
                }

            continuation.invokeOnCancellation { task.cancel(true) }
            task.connect(HeadlessTaskConnector(receiver))
            task.executeParallel()
        }

    internal fun resolvePullPlan(mode: DataPullMode): PullPlan =
        when (mode) {
            DataPullMode.NORMAL -> {
                PullPlan(
                    server = ServerUrls.getDataServerKey(),
                    userId = null,
                    requester = CommCareApplication.instance().dataPullRequester,
                    blockRemoteKeyManagement = false,
                    payloadReferences = emptyList(),
                )
            }

            DataPullMode.CONSUMER_APP -> {
                PullPlan(
                    server = FAKE_SERVER,
                    userId = UNUSED_USER_ID,
                    requester = LocalReferencePullResponseFactory.INSTANCE,
                    blockRemoteKeyManagement = true,
                    payloadReferences = listOf(SingleAppInstallation.LOCAL_RESTORE_REFERENCE),
                )
            }

            DataPullMode.CCZ_DEMO -> {
                val demoUserRestore =
                    CommCareApplication.instance().commCarePlatform.demoUserRestore
                PullPlan(
                    server = FAKE_SERVER,
                    userId = DEMO_USER_ID,
                    requester = LocalReferencePullResponseFactory.INSTANCE,
                    blockRemoteKeyManagement = true,
                    payloadReferences = listOf(demoUserRestore.reference),
                )
            }
        }

    private fun localRestoreReferenceExists(): Boolean =
        try {
            ReferenceManager
                .instance()
                .DeriveReference(SingleAppInstallation.LOCAL_RESTORE_REFERENCE)
                .stream
            true
        } catch (e: Exception) {
            Logger.exception("Local restore reference missing during consumer-app login", e)
            false
        }

    companion object {
        private const val FAKE_SERVER = "fake-server-that-is-never-used"
        private const val UNUSED_USER_ID = "unused"
        private const val DEMO_USER_ID = "demo_id"
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
