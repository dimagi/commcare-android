package org.commcare.login

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApp
import org.commcare.CommCareApplication
import org.commcare.activities.DataPullController
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.ManageKeyRecordTask
import org.commcare.views.notifications.MessageTag
import org.commcare.views.notifications.NotificationActionButtonInfo
import org.commcare.views.notifications.NotificationMessage
import org.javarosa.core.services.Logger

internal sealed class KeyRecordOutcome {
    data class ReadyForSync(
        val password: String,
    ) : KeyRecordOutcome()

    object LocalLoginComplete : KeyRecordOutcome()

    data class Failed(
        val error: LoginError,
    ) : KeyRecordOutcome()
}

/**
 * Suspending wrapper around [ManageKeyRecordTask]: drives key validation/unwrap off the UI thread
 * and reduces the task's callbacks to a single [KeyRecordOutcome].
 */
internal open class KeyRecordOperations(
    private val context: Context,
    private val app: CommCareApp,
) {
    open suspend fun manageKeyRecord(
        request: LoginRequest,
        sink: LoginProgressSink,
    ): KeyRecordOutcome =
        suspendCancellableCoroutine { continuation ->
            sink.onProgress(LoginProgress(LoginPhase.SigningIn))
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
                    ): Unit = throw unexpectedRaiseLoginMessage()

                    override fun raiseLoginMessage(
                        messageTag: MessageTag,
                        showTop: Boolean,
                        buttonAction: NotificationActionButtonInfo.ButtonAction,
                    ): Unit = throw unexpectedRaiseLoginMessage()

                    override fun raiseLoginMessageWithInfo(
                        messageTag: MessageTag,
                        additionalInfo: String?,
                        showTop: Boolean,
                    ): Unit = throw unexpectedRaiseLoginMessage()

                    override fun raiseMessage(
                        message: NotificationMessage,
                        showTop: Boolean,
                    ) {
                        // Fired as a non-terminal warning (e.g. multiple sandboxes) on an otherwise successful login.
                        if (showTop) {
                            CommCareApplication.notificationManager().reportNotificationMessage(message)
                        }
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
                    request.dataPullMode,
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
                        Logger.exception("Error while managing key record during login", e)

                        if (e is CancellationException) {
                            throw e
                        }

                        continuation.resumeOnce(
                            KeyRecordOutcome.Failed(
                                LoginError.UnknownFailure(e.message),
                            ),
                        )
                    }
                }

            continuation.invokeOnCancellation { task.cancel(true) }
            task.connect(HeadlessTaskConnector(receiver))
            task.executeParallel()
        }

    private fun unexpectedRaiseLoginMessage(): IllegalStateException =
        IllegalStateException(
            "ManageKeyRecordTask failures are routed through keysDoneOther/OutcomeMapper; " +
                "raiseLoginMessage should not be reached",
        )

    companion object {
        private const val TASK_ID = 0x4c47 // unique id for this engine; 'LG' in ASCII
    }
}
