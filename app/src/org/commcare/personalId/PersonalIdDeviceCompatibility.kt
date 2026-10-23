package org.commcare.personalId

import android.os.Build
import org.commcare.CommCareApplication

/**
 * Whether this device can run PersonalID at all. Independent of whether a user is signed in.
 */
object PersonalIdDeviceCompatibility {
    private const val MAX_SCREEN_WIDTH_DP = 600

    /**
     * PersonalID needs the Android P biometric APIs, and is phone-only.
     */
    @JvmStatic
    fun isSupported(): Boolean {
        val smallestWidthDp =
            CommCareApplication
                .instance()
                .resources.configuration.smallestScreenWidthDp
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && smallestWidthDp < MAX_SCREEN_WIDTH_DP
    }
}
