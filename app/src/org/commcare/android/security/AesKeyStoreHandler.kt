package org.commcare.android.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import org.commcare.utils.EncryptionKeyAndTransform
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Default encryption key handler for Android Key Store
 */
@RequiresApi(Build.VERSION_CODES.M)
class AesKeyStoreHandler(
    private val alias: String,
    private val needsUserAuth: Boolean
) : KeyStoreHandler {

    override fun getKeyOrGenerate(): EncryptionKeyAndTransform {
        val keystore = AndroidKeyStore.instance
        val entry = keystore.getEntry(alias, null)
        val key = if (entry is KeyStore.SecretKeyEntry) {
            entry.secretKey
        } else {
            generateAesKey(alias, needsUserAuth)
        }
        return EncryptionKeyAndTransform(
            key,
            "AES/CBC/PKCS7Padding"
        )
    }

    private fun generateAesKey(alias: String, needsUserAuth: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            AndroidKeyStore.ANDROID_KEY_STORE_NAME
        )
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setUserAuthenticationRequired(needsUserAuth)
            builder.setInvalidatedByBiometricEnrollment(needsUserAuth)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
}
