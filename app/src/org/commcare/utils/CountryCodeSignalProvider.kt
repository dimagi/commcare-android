package org.commcare.utils

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.os.ConfigurationCompat
import org.commcare.CommCareApplication

/**
 * Reads country signals from Android system services for determining
 * the user's country code.
 */
class CountryCodeSignalProvider {
    private val appContext: Context = CommCareApplication.instance()
    private val telephonyManager: TelephonyManager? =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    /** SIM card country ISO (e.g., "in", "ke"). Empty if unavailable. */
    val simCountryIso: String
        get() = telephonyManager?.simCountryIso ?: ""

    /** Cellular network country ISO (e.g., "in", "ke"). Empty if unavailable. */
    val networkCountryIso: String
        get() = telephonyManager?.networkCountryIso ?: ""

    /** Device locale country ISO (e.g., "US", "IN"). Empty if unavailable. */
    val localeCountry: String
        get() = ConfigurationCompat.getLocales(appContext.resources.configuration)
            .get(0)?.country ?: ""
}
