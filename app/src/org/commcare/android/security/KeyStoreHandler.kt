package org.commcare.android.security

import org.commcare.utils.EncryptionKeyAndTransform

interface KeyStoreHandler {
    fun getKeyOrGenerate(): EncryptionKeyAndTransform
    fun isKeyValid(): Boolean
}
