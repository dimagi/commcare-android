package org.commcare.services

import android.os.Build
import androidx.annotation.RequiresApi
import org.commcare.CommCareApplication
import org.commcare.android.security.AesKeyStoreHandler
import org.commcare.android.security.AndroidKeyStore
import org.commcare.utils.EncryptionKeyAndTransform
import java.security.Key

/**
 * Currently used to manage the session Android keystore backed encryption key,
 */
@RequiresApi(Build.VERSION_CODES.M)
class SessionManager {
    companion object {
        private const val SESSION_KEY_ALIAS = "session_encryption_key"

        private val sessionKeyAndTransformation by lazy {
            AesKeyStoreHandler(SESSION_KEY_ALIAS, needsUserAuth = false).getKeyOrGenerate()
        }

        @JvmStatic
        fun retrieveSessionKeyAndTransformation(): EncryptionKeyAndTransform = sessionKeyAndTransformation

        // null represents that the Android Keystore is supported and the key to be used should be retrieved there
        @JvmStatic
        fun generateLegacyKeyOrNull(): ByteArray? =
            if (AndroidKeyStore.isKeystoreAvailable()) {
                null
            } else {
                CommCareApplication.instance().createNewSymmetricKey().encoded
            }
    }
}
