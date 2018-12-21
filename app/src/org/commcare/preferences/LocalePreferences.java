package org.commcare.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.ViewCompat;
import android.util.Log;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.utils.ChangeLocaleUtil;
import org.javarosa.core.services.locale.Localization;

import java.util.Locale;

public class LocalePreferences {
    private static String sDefaultDeviceLocale = null;
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

    @Nullable
    private static String getUserChosenLocale() {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        if (app != null) return null;
        SharedPreferences prefs = app.getAppPreferences();
        return prefs.getString(PREFS_LOCALE_KEY, null);
    }

    public static boolean isLocaleRTL() {
        if (isRtl != null) return isRtl;
        if (CommCareApplication.instance().getCurrentApp() == null) return false;
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        isRtl = prefs.getBoolean(PREF_IS_LOCALE_RTL, false);
        return isRtl;
    }

    /**
     * <p>Creates updated context with new locale configuration. If user didn't chose any locale from available list,
     * device level locale will be used,if it's in the available list. Locale,
     * which is set by {@link #saveDeviceLocale(String)}, will be used as device level locale.
     * Otherwise, default locale will be used.
     * </p>
     * <p>
     * NOTE: It will update default locale with {@link Locale#setDefault(Locale)}.
     * </p>
     *
     * @param context old context to be updated.
     * @return updated context.
     */
    public static Context generateContextWithUpdatedLocale(Context context) {
        try {
            String localeToUse = decideApplicationLocale();
            Locale newLocale = updateLocale(localeToUse);
            return getContextWithLocale(context, newLocale);
        } catch (Exception ignored) {
            Log.e("LocalePref", ignored.getMessage(), ignored);
            // Localization is not yet initialized
        }
        return context;
    }

    private static Locale updateLocale(String localeToUse) {
        Locale newLocale = new Locale(localeToUse);
        Locale.setDefault(newLocale);
        Localization.setLocale(localeToUse);
        return newLocale;
    }

    private static String decideApplicationLocale() throws Exception {
        if (getUserChosenLocale() == null && sDefaultDeviceLocale != null && isLocaleAvailable(sDefaultDeviceLocale)) {
            return sDefaultDeviceLocale;
        }

        return Localization.getCurrentLocale(); // default locale, or user chosen locale
    }

    private static boolean isLocaleAvailable(String locale) {
        String[] availableLocales = ChangeLocaleUtil.getLocaleCodes();
        for (String availableLocale : availableLocales) {
            if (availableLocale.equals(locale)) {
                return true;
            }
        }
        return false;
    }

    private static Context getContextWithLocale(Context context, Locale locale) {
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
