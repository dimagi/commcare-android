package org.commcare.utils

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object HashUtils {

    enum class HashAlgorithm(val algorithmName: String) {
        SHA1("SHA-1"),
        SHA256("SHA-256");

        override fun toString(): String = algorithmName
    }

    @JvmStatic
    fun computeHash(message: String, algorithm: HashAlgorithm = HashAlgorithm.SHA256): String {
        val digest = MessageDigest.getInstance(algorithm.algorithmName)
        val hashBytes = digest.digest(message.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
