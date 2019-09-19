package org.commcare.preferences;

import androidx.core.text.TextUtilsCompat;
import androidx.core.view.ViewCompat;

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
