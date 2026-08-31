package org.commcare.login

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.CommCareApplication

/**
 * Activity-free entry point for the login pipeline: resolves credentials, manages the key record,
 * syncs, re-binds the session, and runs post-success side effects, producing a [LoginResult].
 */
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
        listener: LoginProgressListener,
        callback: ResultCallback,
    ): Job =
        lifecycleOwner.lifecycleScope.launch {
            val result = performLogin(request, listener)
            callback.onResult(result)
        }

    suspend fun performLogin(
        request: LoginRequest,
        listener: LoginProgressListener,
    ): LoginResult {
        val effectiveRequest =
            if (request.authSource == AuthSource.PersonalId) {
                val record =
                    credentialResolver.resolve(
                        appId = request.appId,
                        username = request.username,
                        createIfNeeded = true,
                    )
                request.copy(passwordOrPin = record.password)
            } else {
                request
            }

        return when (val keyOutcome = keyRecordOperations.manageKeyRecord(effectiveRequest, listener)) {
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
                            listener = listener,
                        )
                ) {
                    is SyncOutcome.Success -> finalizeAfterSync(effectiveRequest, listener)
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
        listener: LoginProgressListener,
    ): LoginResult {
        val rebindRequest = request.copy(blockRemoteKeyManagement = true)
        return when (val keyOutcome = keyRecordOperations.manageKeyRecord(rebindRequest, listener)) {
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
        return LoginResult.Success(
            appId = request.appId,
            username = request.username,
            loginMode = request.credentialType,
            restoreSession = request.restoreSession,
            personalIdManagedLogin = request.authSource == AuthSource.PersonalId,
            linkPassword = request.passwordOrPin,
            postLoginOutcome = postLoginOutcome,
        )
    }
}
