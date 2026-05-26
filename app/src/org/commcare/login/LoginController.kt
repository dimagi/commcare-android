package org.commcare.login

import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.connect.PersonalIdManager

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
        if (request.authSource == AuthSource.Demo) {
            return when (val outcome = demoLoginPath.login(sink)) {
                SyncOutcome.Success -> finishSuccess(activity, request)
                is SyncOutcome.Failed -> LoginResult.Failed(outcome.error)
            }
        }

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
            is KeyRecordOutcome.LocalLoginComplete -> finishSuccess(activity, effectiveRequest)
            is KeyRecordOutcome.ReadyForSync -> {
                when (
                    val syncOutcome =
                        syncOperations.pullData(
                            username = effectiveRequest.username,
                            password = keyOutcome.password,
                            sink = sink,
                        )
                ) {
                    is SyncOutcome.Success -> finishSuccess(activity, effectiveRequest)
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
            manualSwitchToPwMode = false,
            personalIdManagedLogin = isConnectManaged || PersonalIdManager.getInstance().isloggedIn(),
            connectManagedLogin = isConnectManaged,
            postLoginOutcome = postLoginOutcome,
        )
    }
}
