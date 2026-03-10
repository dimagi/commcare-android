package org.commcare.utils

/**
 * Fake implementation for testing country code signal priority logic.
 * Set fields directly to simulate any combination of SIM/network/locale.
 */
class FakeCountryCodeSignalProvider : CountryCodeSignalProvider {
    override var simCountryIso: String = ""
    override var networkCountryIso: String = ""
    override var localeCountry: String = ""
}
