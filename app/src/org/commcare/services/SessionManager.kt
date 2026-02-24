package org.commcare.services

import android.os.Build
import androidx.annotation.RequiresApi
import org.commcare.android.security.AesKeyStoreHandler
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
        fun getEncryptionKey(): Key = sessionKeyAndTransformation.key

        @JvmStatic
        fun getKeyTransformation(): String = sessionKeyAndTransformation.transformation
    }
}
