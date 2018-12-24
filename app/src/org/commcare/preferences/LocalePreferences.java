package org.commcare.preferences;

import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.ViewCompat;

import java.util.Locale;

public class LocalePreferences {
    private static boolean isRtl = false;

    public static void saveDeviceLocale(Locale deviceLocale) {
        isRtl = TextUtilsCompat.getLayoutDirectionFromLocale(deviceLocale) == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    public static boolean isLocaleRTL() {
        return isRtl;
    }
}
