package org.commcare.android.database.connect.models

import androidx.annotation.StringDef

/**
 * Data holder for personal identification session state during Connect ID flows.
 * This includes authentication configuration and account-related metadata.
 */
data class PersonalIdSessionData(
        @DeviceAuthType var requiredLock: String? = null, //Tells which device auth is required for the given user
        var demoUser: Boolean? = null, //states weather it is a demo user or normal user
        var token: String? = null, // session token
        var sessionFailureCode: String? = null, // Reason code to tell why user is not allowed to move forward with the flow
        var sessionFailureSubcode: String? = null, // Sub Reason code to tell why user is not allowed to move forward with the flow
        var accountExists: Boolean? = null, // Tells weather its new user or old
        var photoBase64: String? = null, // photo of the user
        var username: String? = null, // name os the user
        var dbKey: String? = null, // DB Key
        var oauthPassword: String? = null, // password to verify usser
        var accountOrphaned: Boolean? = null // Nobody owns this account
) {

    /**
     * Annotation to restrict accepted authentication types used by the device.
     * Only DEVICE_TYPE or BIOMETRIC_TYPE are allowed.
     */
    @StringDef(DEVICE_TYPE, BIOMETRIC_TYPE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class DeviceAuthType

    companion object {
        const val DEVICE_TYPE = "device"
        const val BIOMETRIC_TYPE = "biometric"
    }
}
