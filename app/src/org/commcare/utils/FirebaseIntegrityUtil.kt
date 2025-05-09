package org.commcare.utils

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import org.javarosa.core.services.Logger

object FirebaseIntegrityUtil {
    /**
     * Retrieves a Firebase Integrity token synchronously.
     *
     * @param context The application context.
     * @param nonce A unique and random string (recommended to be cryptographically secure).
     * @param timeoutSeconds Time to wait for the token request to complete.
     * @return The integrity token string if successful, null otherwise.
     */
    @JvmStatic
    fun getIntegrityTokenSync(
        context: Context,
        nonce: String,
        timeoutSeconds: Long = 10
    ): String? {
        return try {
            val integrityManager: IntegrityManager = IntegrityManagerFactory.create(context)
            val request = IntegrityTokenRequest.builder().setNonce(nonce).build()
            val task = integrityManager.requestIntegrityToken(request)

            // Wait synchronously for the result (NOT for UI thread)
            val response = Tasks.await(task, timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            Logger.log("Integrity test", "Integrity token: ${response.token()}")
            response.token()
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.exception("Integrity test", e)
            null
        }
    }
}