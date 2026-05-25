package org.commcare.login

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApp
import org.commcare.activities.DataPullController
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.ManageKeyRecordTask
import org.commcare.tasks.templates.CommCareTask
import org.commcare.tasks.templates.CommCareTaskConnector
import org.commcare.views.notifications.MessageTag
import org.commcare.views.notifications.NotificationActionButtonInfo
import org.commcare.views.notifications.NotificationMessage
import kotlin.coroutines.resume

/**
 * Outcome of a key-record exchange.
 */
internal sealed class KeyRecordOutcome {
    /** Keys are in place but sync still required. */
    data class ReadyForSync(
        val password: String,
        val pullMode: DataPullMode,
    ) : KeyRecordOutcome()

    /** Local-only login succeeded — sandbox already populated. */
    object LocalLoginComplete : KeyRecordOutcome()

    data class Failed(
        val error: LoginError,
    ) : KeyRecordOutcome()
}

/**
 * Suspending wrapper around ManageKeyRecordTask. Emits SigningIn progress events.
 *
 * The wrapper bridges the AsyncTask receiver-callback model to a suspend function:
 * - When the task calls receiver.startDataPull(mode, password), resume with ReadyForSync.
 * - When the task calls receiver.dataPullCompleted(), resume with LocalLoginComplete.
 * - When the task calls receiver.raiseLoginMessage / raiseMessage, the failure is captured
 *   via keysDoneOther override which has the original HttpCalloutOutcomes value.
 *
 * Cancellation is best-effort — AsyncTask cancellation does not preempt running steps.
 */
internal open class KeyRecordOperations(
    private val context: Context,
    private val app: CommCareApp,
) {
    open suspend fun manageKeyRecord(
        request: LoginRequest,
        sink: LoginProgressSink,
    ): KeyRecordOutcome =
        suspendCancellableCoroutine { cont ->
            val receiver =
                object : DataPullController {
                    override fun startDataPull(
                        mode: DataPullMode,
                        password: String,
                    ) {
                        if (!cont.isCompleted) cont.resume(KeyRecordOutcome.ReadyForSync(password, mode))
                    }

                    override fun dataPullCompleted() {
                        if (!cont.isCompleted) cont.resume(KeyRecordOutcome.LocalLoginComplete)
                    }

                    // Error-path callbacks are no-ops on the receiver; failure is captured via
                    // keysDoneOther override below, which carries the original HttpCalloutOutcomes value.
                    override fun raiseLoginMessage(
                        messageTag: MessageTag,
                        showTop: Boolean,
                    ) = Unit

                    override fun raiseLoginMessage(
                        messageTag: MessageTag,
                        showTop: Boolean,
                        buttonAction: NotificationActionButtonInfo.ButtonAction,
                    ) = Unit

                    override fun raiseLoginMessageWithInfo(
                        messageTag: MessageTag,
                        additionalInfo: String?,
                        showTop: Boolean,
                    ) = Unit

                    override fun raiseMessage(
                        message: NotificationMessage,
                        showTop: Boolean,
                    ) = Unit
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
                    request.pullMode,
                ) {
                    override fun deliverUpdate(
                        receiver: DataPullController,
                        vararg update: String,
                    ) {
                        sink.onProgress(LoginProgress(LoginPhase.SigningIn, message = update.firstOrNull()))
                    }

                    override fun keysDoneOther(
                        receiver: DataPullController,
                        outcome: HttpCalloutOutcomes,
                    ) {
                        // Intercept the failure path before the base class drives the no-op receiver.
                        if (!cont.isCompleted) {
                            cont.resume(KeyRecordOutcome.Failed(OutcomeMapper.fromHttpCalloutOutcome(outcome)))
                        }
                    }

                    override fun deliverError(
                        receiver: DataPullController,
                        e: Exception,
                    ) {
                        if (!cont.isCompleted) {
                            cont.resume(KeyRecordOutcome.Failed(LoginError.SyncFailed("UNKNOWN", e.message)))
                        }
                    }
                }

            // A minimal CommCareTaskConnector that routes callbacks to our receiver.
            // startBlockingForTask / stopBlockingForTask are no-ops because we have no UI
            // progress dialog; the coroutine suspension itself serves as the "blocking" mechanism.
            val connector =
                object : CommCareTaskConnector<DataPullController> {
                    override fun <A, B, C> connectTask(task: CommCareTask<A, B, C, DataPullController>) = Unit

                    override fun startBlockingForTask(id: Int) = Unit

                    override fun stopBlockingForTask(id: Int) = Unit

                    override fun taskCancelled() = Unit

                    override fun getReceiver(): DataPullController = receiver

                    override fun startTaskTransition() = Unit

                    override fun stopTaskTransition(taskId: Int) = Unit

                    override fun hideTaskCancelButton() = Unit
                }

            cont.invokeOnCancellation { task.cancel(true) }
            task.connect(connector)
            task.executeParallel()
        }

    companion object {
        private const val TASK_ID = 0x4c47 // unique id for this engine; 'LG' in ASCII
    }
}
