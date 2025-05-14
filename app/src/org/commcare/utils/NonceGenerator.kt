package org.commcare.utils

import android.util.Base64
import java.security.SecureRandom

object NonceGenerator {
    /**
     * Generates a random 24-Byte nonce string.
     *
     * @return A Base64-encoded nonce string.
     */
    fun generateNonce(): String {
        val nonceBytes = ByteArray(24)
        SecureRandom().nextBytes(nonceBytes)
        return Base64.encodeToString(nonceBytes, Base64.NO_WRAP)
    }
}