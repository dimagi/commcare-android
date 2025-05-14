package org.commcare.utils

import android.content.Context
import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import org.javarosa.core.services.Logger
import java.security.SecureRandom

object GooglePlayIntegrityUtil {
    /**
     * Retrieves a Google Play Integrity token asynchronously.
     *
     * @param context The application context.
     * @param nonce A unique nonce string for the integrity token request.
     * @param timeoutSeconds Time to wait for the token request to complete.
     * @return The integrity token string if successful, null otherwise.
     */
    fun getIntegrityTokenAsync(
        context: Context,
        nonce: String,
        timeoutSeconds: Long,
        callback: (String?) -> Void
    ) {
        require(nonce.isNotBlank()) { "Nonce cannot be empty" }
        require(timeoutSeconds > 0) { "Timeout must be greater than 0" }

        Thread {
            try {
                val integrityManager: IntegrityManager = IntegrityManagerFactory.create(context)
                val request = IntegrityTokenRequest.builder().setNonce(nonce).build()
                val task = integrityManager.requestIntegrityToken(request)

                val response = Tasks.await(task, timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                callback(response.token())
            } catch (e: Exception) {
                e.printStackTrace()
                Logger.exception("Error retrieving Google Play Integrity token", e)
                callback(null)
            }
        }.start()
    }

    fun generateNonce(): String {
        val nonceBytes = ByteArray(24)
        SecureRandom().nextBytes(nonceBytes)
        return Base64.encodeToString(nonceBytes, Base64.NO_WRAP)
    }
}