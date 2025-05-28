package org.commcare.android.database.connect.models

import androidx.annotation.StringDef

/**
 * Data holder for personal identification session state during Personal ID flows.
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
        var userId: String? = null, // username given by server
        var dbKey: String? = null, // DB Key
        var oauthPassword: String? = null, // password to verify usser
        var accountOrphaned: Boolean? = null, // Nobody owns this account
        var userName: String? = null,
        var phoneNumber: String? = null,
) {

    /**
     * Annotation to restrict accepted authentication types used by the device.
     * Only PIN or BIOMETRIC_TYPE are allowed.
     */
    @StringDef(PIN, BIOMETRIC_TYPE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class DeviceAuthType

    companion object {
        const val PIN = "pin"
        const val BIOMETRIC_TYPE = "biometric"
    }
}
