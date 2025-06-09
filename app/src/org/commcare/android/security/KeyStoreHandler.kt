package org.commcare.android.security

import org.commcare.utils.EncryptionKeyAndTransform
import javax.crypto.SecretKey

interface KeyStoreHandler {
    fun getKeyOrGenerate(): EncryptionKeyAndTransform
    fun isKeyValid(): Boolean
}
