package org.commcare.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.ViewCompat;

import org.commcare.CommCareApplication;
import org.javarosa.core.services.locale.Localization;

import java.util.Locale;

public class LocalePreferences {
    private static String sDefaultDeviceLocale;
    private final static String PREF_IS_LOCALE_RTL = "is_cur_locale_rtl";
    private final static String PREFS_LOCALE_KEY = "cur_locale";

    private static Boolean isRtl = null;

    public static void saveCurrentLocale(String currentLocale) {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        prefs.edit().putString(PREFS_LOCALE_KEY, currentLocale).apply();
        updateLocaleRTLPrefs(currentLocale);
    }

    @SuppressLint("ApplySharedPref")
    private static void updateLocaleRTLPrefs(String locale) {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        isRtl = TextUtilsCompat.getLayoutDirectionFromLocale(new Locale(locale)) == ViewCompat.LAYOUT_DIRECTION_RTL;
        prefs.edit().putBoolean(PREF_IS_LOCALE_RTL,
                isRtl)
                .commit();
    }

    public static boolean isLocaleRTL() {
        if (isRtl != null) return isRtl;
        if (CommCareApplication.instance().getCurrentApp() == null) return false;
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        isRtl = prefs.getBoolean(PREF_IS_LOCALE_RTL, false);
        return isRtl;
    }

    /**
     * Creates new context with updated configuration locale. If user didn't chose any locale from available list,
     * device level locale will be used, which is set by {@link #saveDeviceLocale(String)}.
     * It will update default locale with {@link Locale#setDefault(Locale)}.
     *
     * @param context old context to be updated.
     * @return updated context.
     */
    public static Context generateContextWithUpdatedLocale(Context context) {
        try {
            String localeToUse = "";
            return updateResources(context, Localization.getCurrentLocale());
        } catch (Exception ignored) {
            // Localization is not yet initialized
        }
        return context;
    }

    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        return context;
    }

    public static void saveDeviceLocale(String deviceLocale) {
        sDefaultDeviceLocale = deviceLocale;
    }
}
