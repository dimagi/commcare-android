package org.commcare.android.security

import java.security.KeyStore

/**
 * Maintains a singleton instance of Android Key Store
 */
object AndroidKeyStore {
    const val ANDROID_KEY_STORE_NAME = "AndroidKeyStore"
    val instance: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEY_STORE_NAME).apply {
            load(null)
        }
    }
}
