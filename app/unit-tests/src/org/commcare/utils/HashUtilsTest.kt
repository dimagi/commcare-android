package org.commcare.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class HashUtilsTest {

    // Known reference values (RFC 4648 base64, no line wrap):
    // SHA-256("hello") = "LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ="
    // SHA-1("hello")   = "qvTGHdzF6KLavt4PO0gs2a6pQ00="
    // SHA-256("")      = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
    // SHA-1("")        = "2jmj7l5rSw0yVb/vlWAYkK/YBwk="

    @Test
    fun computeHash_sha256_producesKnownBase64ForHello() {
        assertEquals(
            "LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=",
            HashUtils.computeHash("hello", HashUtils.HashAlgorithm.SHA256)
        )
    }

    @Test
    fun computeHash_sha1_producesKnownBase64ForHello() {
        assertEquals(
            "qvTGHdzF6KLavt4PO0gs2a6pQ00=",
            HashUtils.computeHash("hello", HashUtils.HashAlgorithm.SHA1)
        )
    }

    @Test
    fun computeHash_sha256_producesKnownBase64ForEmptyString() {
        assertEquals(
            "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=",
            HashUtils.computeHash("", HashUtils.HashAlgorithm.SHA256)
        )
    }

    @Test
    fun computeHash_sha1_producesKnownBase64ForEmptyString() {
        assertEquals(
            "2jmj7l5rSw0yVb/vlWAYkK/YBwk=",
            HashUtils.computeHash("", HashUtils.HashAlgorithm.SHA1)
        )
    }

    @Test
    fun computeHash_defaultAlgorithm_matchesExplicitSha256() {
        assertEquals(
            HashUtils.computeHash("test", HashUtils.HashAlgorithm.SHA256),
            HashUtils.computeHash("test")
        )
    }

    @Test
    fun computeHash_isDeterministic() {
        val first = HashUtils.computeHash("hello", HashUtils.HashAlgorithm.SHA256)
        val second = HashUtils.computeHash("hello", HashUtils.HashAlgorithm.SHA256)
        assertEquals(first, second)
    }

    @Test
    fun computeHash_differentInputsProduceDifferentHashes() {
        val hashA = HashUtils.computeHash("hello", HashUtils.HashAlgorithm.SHA256)
        val hashB = HashUtils.computeHash("world", HashUtils.HashAlgorithm.SHA256)
        assertNotEquals(hashA, hashB)
    }

    @Test
    fun computeHash_sha1AndSha256ProduceDifferentHashesForSameInput() {
        val sha1 = HashUtils.computeHash("hello", HashUtils.HashAlgorithm.SHA1)
        val sha256 = HashUtils.computeHash("hello", HashUtils.HashAlgorithm.SHA256)
        assertNotEquals(sha1, sha256)
    }

    @Test
    fun hashAlgorithm_sha1_toStringReturnsAlgorithmName() {
        assertEquals("SHA-1", HashUtils.HashAlgorithm.SHA1.toString())
    }

    @Test
    fun hashAlgorithm_sha256_toStringReturnsAlgorithmName() {
        assertEquals("SHA-256", HashUtils.HashAlgorithm.SHA256.toString())
    }
}
