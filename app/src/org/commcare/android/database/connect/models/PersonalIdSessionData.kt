package org.commcare.android.database.connect.models

import androidx.annotation.StringDef

/**
 * Data holder for personal identification session state during Personal ID flows.
 * This includes authentication configuration and account-related metadata.
 */
data class PersonalIdSessionData(
    // Tells which device auth is required for the given user
    @DeviceAuthType var requiredLock: String? = null,
    // states weather it is a demo user or normal user
    var demoUser: Boolean? = null,
    // session token
    var token: String? = null,
    // Reason code to tell why user is not allowed to move forward with the flow
    var sessionFailureCode: String? = null,
    // Sub Reason code to tell why user is not allowed to move forward with the flow
    var sessionFailureSubcode: String? = null,
    // Tells weather its new user or old
    var accountExists: Boolean? = null,
    // photo of the user
    var photoBase64: String? = null,
    // username given by server
    var personalId: String? = null,
    // DB Key
    var dbKey: String? = null,
    // password to verify usser
    var oauthPassword: String? = null,
    // number of attempts left before the account lock
    var attemptsLeft: Int? = null,
    // name of the user
    var userName: String? = null,
    // phone number of the user
    var phoneNumber: String? = null,
    // recovery code of the user
    var backupCode: String? = null,
    var smsMethod: String? = null,
    // indicates if user has has been invited to Connect
    var invitedUser: Boolean = false,
    var otpFallback: Boolean = false,
    // the number of times the user hit the "resend code" button
    var otpReattempts: Int = 0,
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
