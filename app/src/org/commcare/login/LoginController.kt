package org.commcare.login

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.CommCareApplication
import org.commcare.connect.PersonalIdManager

class LoginController internal constructor(
    private val keyRecordOperations: KeyRecordOperations,
    private val syncOperations: SyncOperations,
    private val credentialResolver: ConnectCredentialResolver,
    private val postLoginSideEffects: PostLoginSideEffects,
) {
    constructor(context: Context) : this(
        keyRecordOperations = KeyRecordOperations(context, CommCareApplication.instance().currentApp),
        syncOperations = SyncOperations(context),
        credentialResolver = ConnectCredentialResolver(context),
        postLoginSideEffects = PostLoginSideEffects(context),
    )

    fun interface ResultCallback {
        fun onResult(result: LoginResult)
    }

    fun start(
        lifecycleOwner: LifecycleOwner,
        request: LoginRequest,
        sink: LoginProgressSink,
        callback: ResultCallback,
    ): Job =
        lifecycleOwner.lifecycleScope.launch {
            val result = performLogin(request, sink)
            callback.onResult(result)
        }

    suspend fun performLogin(
        request: LoginRequest,
        sink: LoginProgressSink,
    ): LoginResult {
        val effectiveRequest =
            if (request.authSource == AuthSource.AutoFromConnect ||
                request.authSource == AuthSource.PersonalIdManaged
            ) {
                val record =
                    credentialResolver.resolve(
                        appId = request.appId,
                        username = request.username,
                        createIfNeeded = request.authSource == AuthSource.AutoFromConnect,
                    )
                request.copy(passwordOrPin = record.password)
            } else {
                request
            }

        return when (val keyOutcome = keyRecordOperations.manageKeyRecord(effectiveRequest, sink)) {
            is KeyRecordOutcome.Failed -> {
                LoginResult.Failed(keyOutcome.error)
            }

            is KeyRecordOutcome.LocalLoginComplete -> {
                finishSuccess(effectiveRequest)
            }

            is KeyRecordOutcome.ReadyForSync -> {
                when (
                    val syncOutcome =
                        syncOperations.pullData(
                            username = effectiveRequest.username,
                            password = keyOutcome.password,
                            mode = effectiveRequest.dataPullMode,
                            sink = sink,
                        )
                ) {
                    is SyncOutcome.Success -> finalizeAfterSync(effectiveRequest, sink)
                    is SyncOutcome.Failed -> LoginResult.Failed(syncOutcome.error)
                }
            }
        }
    }

    /**
     * Re-runs the local key-record path after a successful remote sync so the session service is
     * rebound with the user now persisted in the sandbox.
     */
    private suspend fun finalizeAfterSync(
        request: LoginRequest,
        sink: LoginProgressSink,
    ): LoginResult {
        val rebindRequest = request.copy(blockRemoteKeyManagement = true)
        return when (val keyOutcome = keyRecordOperations.manageKeyRecord(rebindRequest, sink)) {
            is KeyRecordOutcome.LocalLoginComplete -> finishSuccess(rebindRequest)
            is KeyRecordOutcome.Failed -> LoginResult.Failed(keyOutcome.error)
            is KeyRecordOutcome.ReadyForSync -> LoginResult.Failed(LoginError.BadCredentials)
        }
    }

    private suspend fun finishSuccess(request: LoginRequest): LoginResult.Success {
        val postLoginOutcome =
            withContext(NonCancellable) {
                postLoginSideEffects.runOnSuccess(request.username)
            }
        val isConnectManaged = request.authSource == AuthSource.AutoFromConnect

        return LoginResult.Success(
            appId = request.appId,
            username = request.username,
            loginMode = request.credentialType,
            restoreSession = request.restoreSession,
            personalIdManagedLogin = isConnectManaged || PersonalIdManager.getInstance().isloggedIn(),
            connectManagedLogin = isConnectManaged,
            linkPassword = request.passwordOrPin,
            postLoginOutcome = postLoginOutcome,
        )
    }
}
