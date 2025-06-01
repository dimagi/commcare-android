package org.commcare.android.security

import org.commcare.utils.EncryptionKeyAndTransform

interface KeystoreHandler {
    fun getKeyOrGenerate(): EncryptionKeyAndTransform
}
