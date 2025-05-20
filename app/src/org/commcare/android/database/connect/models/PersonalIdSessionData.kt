package org.commcare.android.database.connect.models

import androidx.annotation.StringDef

class PersonalIdSessionData  // Private constructor
private constructor() {
    @StringDef(*[DEVICE_TYPE, BIOMETRIC_TYPE])
    @Retention(AnnotationRetention.SOURCE)
    annotation class acceptedPinType

    // Getters and setters
    // Fields from various payloads
    @JvmField
    @acceptedPinType
    var requiredLock: String? = null
    @JvmField
    var demoUser: Boolean? = null
    @JvmField
    var token: String? = null
    @JvmField
    var sessionFailureCode: String? = null
    @JvmField
    var sessionFailureSubcode: String? = null
    var accountExists: Boolean? = null
    var photoBase64: String? = null
    var username: String? = null
    var dbKey: String? = null
    var oauthPassword: String? = null
    var accountOrphaned: Boolean? = null

    // Optional: Clear all data
    fun clear() {
        instance = null
    }

    companion object {
        // Singleton instance
        private var instance: PersonalIdSessionData? = null
        private const val DEVICE_TYPE = "device"
        private const val BIOMETRIC_TYPE = "biometric"

        // Singleton accessor
        @JvmStatic
        @Synchronized
        fun getInstance(): PersonalIdSessionData? {
            if (instance == null) {
                instance = PersonalIdSessionData()
            }
            return instance
        }
    }
}