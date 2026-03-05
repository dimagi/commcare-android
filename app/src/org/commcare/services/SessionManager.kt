package org.commcare.services

import org.commcare.CommCareApplication
import org.commcare.android.security.AesKeyStoreHandler
import org.commcare.android.security.AndroidKeyStore
import org.commcare.utils.EncryptionKeyAndTransform

/**
 * Currently used to manage the session Android keystore backed encryption key,
 */
class SessionManager {
    companion object {
        private const val SESSION_KEY_ALIAS = "session_encryption_key"

        private val sessionKeyAndTransformation by lazy {
            AesKeyStoreHandler(SESSION_KEY_ALIAS, needsUserAuth = false).getKeyOrGenerate()
        }

        @JvmStatic
        fun retrieveSessionKeyAndTransformation(): EncryptionKeyAndTransform = sessionKeyAndTransformation

        /**
         * An empty array indicates that the Android Keystore is supported and the key should be retrieved
         * from the keystore
         */
        @JvmStatic
        fun generateLegacyKeyOrEmpty(): ByteArray =
            if (AndroidKeyStore.isKeystoreAvailable()) {
                ByteArray(0)
            } else {
                CommCareApplication.instance().createNewSymmetricKey().encoded
            }
    }
}
