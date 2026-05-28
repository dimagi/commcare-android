package org.commcare.login

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApp
import org.commcare.activities.DataPullController
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.ManageKeyRecordTask
import org.commcare.views.notifications.MessageTag
import org.commcare.views.notifications.NotificationActionButtonInfo
import org.commcare.views.notifications.NotificationMessage

internal sealed class KeyRecordOutcome {
    data class ReadyForSync(
        val password: String,
    ) : KeyRecordOutcome()

    object LocalLoginComplete : KeyRecordOutcome()

    data class Failed(
        val error: LoginError,
    ) : KeyRecordOutcome()
}

internal open class KeyRecordOperations(
    private val context: Context,
    private val app: CommCareApp,
) {
    open suspend fun manageKeyRecord(
        request: LoginRequest,
        sink: LoginProgressSink,
    ): KeyRecordOutcome =
        suspendCancellableCoroutine { continuation ->
            val receiver =
                object : DataPullController {
                    override fun startDataPull(
                        mode: DataPullMode,
                        password: String,
                    ) {
                        continuation.resumeOnce(KeyRecordOutcome.ReadyForSync(password))
                    }

                    override fun dataPullCompleted() {
                        continuation.resumeOnce(KeyRecordOutcome.LocalLoginComplete)
                    }

                    override fun raiseLoginMessage(
                        messageTag: MessageTag,
                        showTop: Boolean,
                    ) {
                        continuation.resumeOnce(
                            KeyRecordOutcome.Failed(
                                LoginError.SyncFailed(SyncFailureReason.UNKNOWN),
                            ),
                        )
                    }

                    override fun raiseLoginMessage(
                        messageTag: MessageTag,
                        showTop: Boolean,
                        buttonAction: NotificationActionButtonInfo.ButtonAction,
                    ) {
                        continuation.resumeOnce(
                            KeyRecordOutcome.Failed(
                                LoginError.SyncFailed(SyncFailureReason.UNKNOWN),
                            ),
                        )
                    }

                    override fun raiseLoginMessageWithInfo(
                        messageTag: MessageTag,
                        additionalInfo: String?,
                        showTop: Boolean,
                    ) {
                        continuation.resumeOnce(
                            KeyRecordOutcome.Failed(
                                LoginError.SyncFailed(
                                    SyncFailureReason.UNKNOWN,
                                    additionalInfo,
                                ),
                            ),
                        )
                    }

                    override fun raiseMessage(
                        message: NotificationMessage,
                        showTop: Boolean,
                    ) {
                        continuation.resumeOnce(
                            KeyRecordOutcome.Failed(
                                LoginError.SyncFailed(SyncFailureReason.UNKNOWN),
                            ),
                        )
                    }
                }

            val task =
                object : ManageKeyRecordTask<DataPullController>(
                    context,
                    TASK_ID,
                    request.username,
                    request.passwordOrPin,
                    request.credentialType,
                    app,
                    request.restoreSession,
                    request.triggerMultipleUsersWarning,
                    request.blockRemoteKeyManagement,
                    DataPullMode.NORMAL,
                ) {
                    override fun deliverUpdate(
                        receiver: DataPullController,
                        vararg update: String,
                    ) {
                        sink.onProgress(
                            LoginProgress(
                                LoginPhase.SigningIn,
                                message = update.firstOrNull(),
                            ),
                        )
                    }

                    override fun keysDoneOther(
                        receiver: DataPullController,
                        outcome: HttpCalloutOutcomes,
                    ) {
                        continuation.resumeOnce(
                            KeyRecordOutcome.Failed(
                                OutcomeMapper.fromHttpCalloutOutcome(outcome),
                            ),
                        )
                    }

                    override fun deliverError(
                        receiver: DataPullController,
                        e: Exception,
                    ) {
                        if (e is CancellationException) {
                            throw e
                        }

                        continuation.resumeOnce(
                            KeyRecordOutcome.Failed(
                                LoginError.SyncFailed(
                                    SyncFailureReason.UNKNOWN,
                                    e.message,
                                ),
                            ),
                        )
                    }
                }

            continuation.invokeOnCancellation { task.cancel(true) }
            task.connect(HeadlessTaskConnector(receiver))
            task.executeParallel()
        }

    companion object {
        private const val TASK_ID = 0x4c47 // unique id for this engine; 'LG' in ASCII
    }
}
