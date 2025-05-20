package org.commcare.android.database.connect.models

import androidx.annotation.StringDef

class PersonalIdSessionData {

    @StringDef(DEVICE_TYPE, BIOMETRIC_TYPE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class AcceptedPinType

    @AcceptedPinType
    var requiredLock: String? = null
    var demoUser: Boolean? = null
    var token: String? = null
    var sessionFailureCode: String? = null
    var sessionFailureSubcode: String? = null
    var accountExists: Boolean? = null
    var photoBase64: String? = null
    var username: String? = null
    var dbKey: String? = null
    var oauthPassword: String? = null
    var accountOrphaned: Boolean? = null

    fun clear() {
        requiredLock = null
        demoUser = null
        token = null
        sessionFailureCode = null
        sessionFailureSubcode = null
        accountExists = null
        photoBase64 = null
        username = null
        dbKey = null
        oauthPassword = null
        accountOrphaned = null
    }

    companion object {
        private const val DEVICE_TYPE = "device"
        private const val BIOMETRIC_TYPE = "biometric"
    }
}