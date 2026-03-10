package org.commcare.utils

/**
 * Provides country ISO signals for determining the user's country code.
 * Abstracted for testability — production uses TelephonyManager/Locale,
 * tests use a fake implementation.
 */
interface CountryCodeSignalProvider {
    /** SIM card country ISO (e.g., "in", "ke"). Empty/null if unavailable. */
    val simCountryIso: String?

    /** Cellular network country ISO (e.g., "in", "ke"). Empty/null if unavailable. */
    val networkCountryIso: String?

    /** Device locale country ISO (e.g., "US", "IN"). Empty/null if unavailable. */
    val localeCountry: String?
}
