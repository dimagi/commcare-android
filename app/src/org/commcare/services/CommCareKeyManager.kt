package org.commcare.services

import androidx.annotation.VisibleForTesting
import org.commcare.CommCareApplication
import org.commcare.android.security.AesKeyStoreHandler
import org.commcare.android.security.AndroidKeyStore
import org.commcare.utils.EncryptionKeyAndTransform

/**
 * Currently used to manage the session Android keystore backed encryption key,
 */
class CommCareKeyManager {
    companion object {
        private const val SESSION_KEY_ALIAS = "commcare_encryption_key"

        private val sessionKeyAndTransformation by lazy {
            AesKeyStoreHandler(SESSION_KEY_ALIAS, needsUserAuth = false).getKeyOrGenerate()
        }

        @JvmStatic
        fun retrieveSessionKeyAndTransformation(): EncryptionKeyAndTransform =
            testKeyAndTransformation ?: sessionKeyAndTransformation

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

        /**
         * For testing purposes only
         */
        @VisibleForTesting
        private var testKeyAndTransformation: EncryptionKeyAndTransform? = null

        /**
         * Set a test override for the session key. When set, retrieveSessionKeyAndTransformation()
         * returns this value instead of going through AesKeyStoreHandler.
         */
        @JvmStatic
        fun setTestKeyAndTransformation(encryptionKeyAndTransform: EncryptionKeyAndTransform?) {
            testKeyAndTransformation = encryptionKeyAndTransform
        }

        @JvmStatic
        fun clearTestKeyAndTransformation() {
            testKeyAndTransformation = null
        }

    }
}
