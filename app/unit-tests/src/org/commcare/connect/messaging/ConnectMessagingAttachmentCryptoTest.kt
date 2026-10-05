package org.commcare.connect.messaging

import org.commcare.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class ConnectMessagingAttachmentCryptoTest {
    @Test
    fun `decrypts the PersonalID test vector`() {
        val plaintext = ConnectMessagingAttachmentCrypto.decrypt(Base64.decode(TEST_VECTOR_BLOB), TEST_VECTOR_KEY)

        assertEquals("PersonalID attachment test vector", String(plaintext, Charsets.UTF_8))
    }

    @Test(expected = AEADBadTagException::class)
    fun `altered bytes fail authentication`() {
        val blob = Base64.decode(TEST_VECTOR_BLOB)
        blob[20] = (blob[20].toInt() xor 1).toByte()

        ConnectMessagingAttachmentCrypto.decrypt(blob, TEST_VECTOR_KEY)
    }

    @Test(expected = AEADBadTagException::class)
    fun `a different channel key fails authentication`() {
        ConnectMessagingAttachmentCrypto.decrypt(
            Base64.decode(TEST_VECTOR_BLOB),
            "MTIzNDU2Nzg5MGFiY2RlZmdoaWprbG1ub3BxcnM3dXY=",
        )
    }

    @Test(expected = GeneralSecurityException::class)
    fun `a blob shorter than nonce and tag is rejected`() {
        ConnectMessagingAttachmentCrypto.decrypt(ByteArray(27), TEST_VECTOR_KEY)
    }

    private companion object {
        const val TEST_VECTOR_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        const val TEST_VECTOR_BLOB = "oKGio6Slpqeoqaqrtn0OXiqlY9MrIaeycw6hvRjBPH7mlzYJ73oG8BrIAW6gb+xqABsBM0cg1w0oetetHQ=="
    }
}
