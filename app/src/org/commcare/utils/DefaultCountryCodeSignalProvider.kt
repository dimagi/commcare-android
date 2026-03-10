package org.commcare.utils

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.os.ConfigurationCompat

/**
 * Production implementation that reads country signals from Android system services.
 */
class DefaultCountryCodeSignalProvider(context: Context) : CountryCodeSignalProvider {
    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val appContext: Context = context.applicationContext

    override val simCountryIso: String
        get() = telephonyManager?.simCountryIso ?: ""

    override val networkCountryIso: String
        get() = telephonyManager?.networkCountryIso ?: ""

    override val localeCountry: String
        get() = ConfigurationCompat.getLocales(appContext.resources.configuration)
            .get(0)?.country ?: ""
}
