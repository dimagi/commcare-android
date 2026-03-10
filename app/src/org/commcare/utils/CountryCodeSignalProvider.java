package org.commcare.utils;

/**
 * Provides country ISO signals for determining the user's country code.
 * Abstracted for testability — production uses TelephonyManager/Locale,
 * tests use a fake implementation.
 */
public interface CountryCodeSignalProvider {
    /** SIM card country ISO (e.g., "in", "ke"). Empty/null if unavailable. */
    String getSimCountryIso();

    /** Cellular network country ISO (e.g., "in", "ke"). Empty/null if unavailable. */
    String getNetworkCountryIso();

    /** Device locale country ISO (e.g., "US", "IN"). Empty/null if unavailable. */
    String getLocaleCountry();
}
