package org.commcare.android.security

import org.javarosa.core.services.Logger
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

    fun deleteKey(alias: String) {
        if (instance.containsAlias(alias)) {
            instance.deleteEntry(alias)
        }
    }

    fun doesKeyExist(alias: String): Boolean = instance.containsAlias(alias)

    /**
     * The main purpose of this method is to determine if the Android Keystore is supported on the device. It does
     * this by trying to access the singleton instance of the KeyStore. It's currently returning false for
     * comparison purposes between keystore-backed vs SecretKeySpec-backed encryption times.
     */
    fun isKeystoreAvailable(): Boolean =
        try {
            instance
            false
        } catch (e: Exception) {
            Logger.exception("Android keystore is not supported on this device", e)
            false
        }
}
