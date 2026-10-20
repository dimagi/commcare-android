package org.commcare.connect.messaging

import org.commcare.util.Base64
import org.commcare.util.Base64DecoderException
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ConnectMessagingAttachmentCrypto {
    private const val NONCE_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BYTES = 16
    private const val TAG_LENGTH_BITS = TAG_LENGTH_BYTES * 8

    @Throws(GeneralSecurityException::class)
    fun decrypt(
        encrypted: ByteArray,
        base64ChannelKey: String,
    ): ByteArray {
        if (encrypted.size < NONCE_LENGTH_BYTES + TAG_LENGTH_BYTES) {
            throw GeneralSecurityException(
                "Attachment of ${encrypted.size} bytes is shorter than its nonce and tag",
            )
        }
        val key =
            try {
                Base64.decode(base64ChannelKey)
            } catch (e: Base64DecoderException) {
                throw GeneralSecurityException("Channel key is not valid base64", e)
            }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_LENGTH_BITS, encrypted, 0, NONCE_LENGTH_BYTES),
        )
        return cipher.doFinal(encrypted, NONCE_LENGTH_BYTES, encrypted.size - NONCE_LENGTH_BYTES)
    }
}
