package org.commcare.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpErrorTypeTest {
    @Test
    fun `INVALID_CREDENTIAL is recoverable so the user can correct the code or number`() {
        assertFalse(OtpErrorType.INVALID_CREDENTIAL.isNonRecoverable())
    }

    /**
     * Guards the default: Firebase reports unmapped backend codes as a plain FirebaseException,
     * which becomes GENERIC_ERROR, so anything other than INVALID_CREDENTIAL must fall back to
     * PersonalID rather than dead-end. A newly added error type inherits that safe default.
     */
    @Test
    fun `every error other than INVALID_CREDENTIAL is non-recoverable`() {
        OtpErrorType
            .entries
            .filter { it != OtpErrorType.INVALID_CREDENTIAL }
            .forEach {
                assertTrue("$it should be non-recoverable", it.isNonRecoverable())
            }
    }
}
