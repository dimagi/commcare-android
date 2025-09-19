package org.commcare.android.security


import android.content.Context
import android.security.KeyPairGeneratorSpec
import org.commcare.utils.EncryptionKeyAndTransform
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Calendar
import javax.security.auth.x500.X500Principal

/**
 * User for Pre Android M devices to do RSA encryption with Android Key Store
 */
class RsaKeyStoreHandler(
    private val context: Context,
    private val alias: String,
    private val isEncryptMode: Boolean
) : KeyStoreHandler {

    override fun getKeyOrGenerate(): EncryptionKeyAndTransform {
        if (doesKeyExist()) {
            val entry = AndroidKeyStore.instance.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            val key = entry.privateKey
            return EncryptionKeyAndTransform(key, "RSA/ECB/PKCS1Padding")
        } else {
            return generateRsaKey()
        }
    }

    override fun isKeyValid(): Boolean {
        return doesKeyExist()
    }

    override fun deleteKey() {
        AndroidKeyStore.instance.deleteEntry(alias)
    }

    fun doesKeyExist(): Boolean {
        val keystore = AndroidKeyStore.instance
        if (keystore.containsAlias(alias)) {
            val entry = keystore.getEntry(alias, null)
            if (entry is KeyStore.PrivateKeyEntry) {
                return true;
            }
        }
        return false;
    }

    private fun generateRsaKey(): EncryptionKeyAndTransform {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 100) }

        val spec = KeyPairGeneratorSpec.Builder(context)
            .setAlias(alias)
            .setSubject(X500Principal("CN=$alias"))
            .setSerialNumber(BigInteger.ONE)
            .setStartDate(start.time)
            .setEndDate(end.time)
            .build()

        val generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
            initialize(spec)
        }

        val keyPair: KeyPair = generator.generateKeyPair()
        val key = if (isEncryptMode) keyPair.public else keyPair.private
        return EncryptionKeyAndTransform(key, "RSA/ECB/PKCS1Padding")
    }
}


