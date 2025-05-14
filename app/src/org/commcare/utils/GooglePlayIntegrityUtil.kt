package org.commcare.utils

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import org.javarosa.core.services.Logger

object GooglePlayIntegrityUtil {
    /**
     * Retrieves a Google Play Integrity token asynchronously.
     *
     * @param context The application context.
     * @param nonce A unique nonce string for the integrity token request.
     * @param callback A callback function to handle the result. It will be called with the token string
     */
    fun getIntegrityTokenAsync(
        context: Context,
        nonce: String,
        callback: (String?) -> Void
    ) {
        require(nonce.isNotBlank()) { "Nonce cannot be empty" }

        val integrityManager: IntegrityManager = IntegrityManagerFactory.create(context)
        val request = IntegrityTokenRequest.builder().setNonce(nonce).build()
        integrityManager.requestIntegrityToken(request)
            .addOnSuccessListener { response -> callback(response.token()) }
            .addOnFailureListener { exception ->
                Logger.exception("Error retrieving Google Play Integrity token", exception)
                callback(null)
            }
    }
}