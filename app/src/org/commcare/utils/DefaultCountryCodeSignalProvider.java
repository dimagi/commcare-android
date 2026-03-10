package org.commcare.utils;

import android.content.Context;
import android.telephony.TelephonyManager;

/**
 * Production implementation that reads country signals from Android system services.
 */
public class DefaultCountryCodeSignalProvider implements CountryCodeSignalProvider {
    private final TelephonyManager telephonyManager;
    private final Context context;

    public DefaultCountryCodeSignalProvider(Context context) {
        this.context = context;
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public String getSimCountryIso() {
        if (telephonyManager == null) {
            return "";
        }
        return telephonyManager.getSimCountryIso();
    }

    @Override
    public String getNetworkCountryIso() {
        if (telephonyManager == null) {
            return "";
        }
        return telephonyManager.getNetworkCountryIso();
    }

    @Override
    public String getLocaleCountry() {
        return context.getResources().getConfiguration().locale.getCountry();
    }
}
