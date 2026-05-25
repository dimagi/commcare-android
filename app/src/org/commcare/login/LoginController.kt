package org.commcare.login

import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.connect.PersonalIdManager

/**
 * Single entry point for the headless login pipeline.
 *
 * Callers are responsible for unlocking PersonalID (when applicable) before invoking this
 * method. LoginActivity does so via PersonalIdUnlocker.unlock at line 243.
 *
 * Post-success side-effects run in NonCancellable so analytics/notification clears still
 * fire even if the caller cancels after Success was produced.
 */
class LoginController internal constructor(
    private val keyRecordOperations: KeyRecordOperations,
    private val syncOperations: SyncOperations,
    private val demoLoginPath: DemoLoginPath,
    private val credentialResolver: ConnectCredentialResolver,
    private val postLoginSideEffects: PostLoginSideEffects,
) {
    constructor(context: Context) : this(
        keyRecordOperations = KeyRecordOperations(context, CommCareApplication.instance().currentApp),
        syncOperations = SyncOperations(context),
        demoLoginPath = DemoLoginPath(context),
        credentialResolver = ConnectCredentialResolver(context),
        postLoginSideEffects = PostLoginSideEffects(),
    )

    suspend fun performLogin(
        activity: CommCareActivity<*>,
        request: LoginRequest,
        sink: LoginProgressSink,
    ): LoginResult {
        // Demo short-circuit: skip remote key management, run only the demo restore.
        if (request.authSource == AuthSource.Demo) {
            return when (val outcome = demoLoginPath.login(sink)) {
                SyncOutcome.Success -> finishSuccess(activity, request)
                is SyncOutcome.Failed -> LoginResult.Failed(outcome.error)
            }
        }

        // For Connect-managed logins, replace the caller-supplied password with the
        // resolver's value (which may have been generated on first launch).
        val effectiveRequest =
            if (request.authSource == AuthSource.AutoFromConnect) {
                val resolved =
                    credentialResolver.resolve(
                        appId = request.appId,
                        username = request.username,
                        createIfNeeded = true,
                    )
                request.copy(passwordOrPin = resolved.password)
            } else {
                request
            }

        return when (val keyOutcome = keyRecordOperations.manageKeyRecord(effectiveRequest, sink)) {
            is KeyRecordOutcome.Failed -> LoginResult.Failed(keyOutcome.error)
            KeyRecordOutcome.LocalLoginComplete -> finishSuccess(activity, effectiveRequest)
            is KeyRecordOutcome.ReadyForSync -> {
                when (
                    val syncOutcome =
                        syncOperations.pullData(
                            username = effectiveRequest.username,
                            password = keyOutcome.password,
                            sink = sink,
                        )
                ) {
                    SyncOutcome.Success -> finishSuccess(activity, effectiveRequest)
                    is SyncOutcome.Failed -> LoginResult.Failed(syncOutcome.error)
                }
            }
        }
    }

    private suspend fun finishSuccess(
        activity: CommCareActivity<*>,
        request: LoginRequest,
    ): LoginResult.Success {
        val postLoginOutcome =
            withContext(NonCancellable) {
                postLoginSideEffects.runOnSuccess(activity, request.username)
            }
        val isConnectManaged = request.authSource == AuthSource.AutoFromConnect
        return LoginResult.Success(
            loginMode = request.credentialType,
            restoreSession = request.restoreSession,
            // The UI controller's "manually switched to PW mode" state lives on the
            // activity; the caller overwrites this field from its own UI state after
            // performLogin returns. The engine defaults it to false.
            manualSwitchToPwMode = false,
            personalIdManagedLogin = isConnectManaged || PersonalIdManager.getInstance().isloggedIn(),
            connectManagedLogin = isConnectManaged,
            postLoginOutcome = postLoginOutcome,
        )
    }
}
