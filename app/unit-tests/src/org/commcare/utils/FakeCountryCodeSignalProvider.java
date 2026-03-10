package org.commcare.utils;

/**
 * Fake implementation for testing country code signal priority logic.
 * Set fields directly to simulate any combination of SIM/network/locale.
 */
public class FakeCountryCodeSignalProvider implements CountryCodeSignalProvider {
    public String simCountryIso = "";
    public String networkCountryIso = "";
    public String localeCountry = "";

    @Override
    public String getSimCountryIso() {
        return simCountryIso;
    }

    @Override
    public String getNetworkCountryIso() {
        return networkCountryIso;
    }

    @Override
    public String getLocaleCountry() {
        return localeCountry;
    }
}
