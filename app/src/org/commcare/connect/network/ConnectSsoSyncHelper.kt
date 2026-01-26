package org.commcare.connect.network

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.ConnectSsoHelper.TokenCallback
import org.commcare.core.network.AuthInfo
import org.commcare.core.network.AuthInfo.TokenAuth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Created this file as a helper to convert the async call to sync.
 * This could have been achieved by using CompletableFuture, but it supports Android 24, and minSdk is 23.
 * As such, personalId/connect is supported for Android 24 only, but it seems checking for HQ token retrieval is done at many places,
 * so it may lead to crashes for users below Android 24. E.g `CommcareRequestGenerator` is the one using for building the Auth
 * (https://github.com/dimagi/commcare-android/blob/a4062729d1172fb4d710f022edc3670e71031d24/app/src/org/commcare/network/CommcareRequestGenerator.java#L179)
 */
object ConnectSsoSyncHelper {
    @Throws(TokenUnavailableException::class)
    suspend fun retrieveHqSsoTokenASync(
        context: Context,
        user: ConnectUserRecord,
        appRecord: ConnectLinkedAppRecord,
        hqUsername: String?,
        performLink: Boolean,
    ): AuthInfo.TokenAuth? =
        suspendCoroutine { continuation ->

            ConnectSsoHelper.retrieveHqSsoToken(
                context,
                user,
                appRecord,
                hqUsername,
                performLink,
                object : TokenCallback {
                    override fun tokenRetrieved(token: TokenAuth) {
                        continuation.resume(token)
                    }

                    override fun tokenUnavailable() {
                        continuation.resumeWithException(TokenUnavailableException())
                    }

                    override fun tokenRequestDenied() {
                        //TODO DAV: This should change still
                        continuation.resumeWithException(TokenUnavailableException())
                    }
                },
            )
        }

    fun retrieveHqSsoTokenSync(
        context: Context,
        user: ConnectUserRecord,
        appRecord: ConnectLinkedAppRecord,
        hqUsername: String?,
        performLink: Boolean,
    ): AuthInfo.TokenAuth? =
        runBlocking {
            retrieveHqSsoTokenASync(context, user, appRecord, hqUsername, performLink)
        }
}
