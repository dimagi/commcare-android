package org.commcare.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import org.commcare.dalvik.BuildConfig;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.core.util.NoLocalizedTextException;

import java.util.Locale;

public class ChangeLocaleUtil {

    private static String[] removeDefault(String[] raw) {
        String[] output = new String[raw.length - 1];
        int index = 0;
        for (String rawInput : raw) {
            if (!rawInput.equals("default")) {
                output[index] = rawInput;
                index++;
            }
        }
        return output;
    }

    public static String[] translateLocales(String[] raw) {
        String[] translated = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            try {
                translated[i] = Localization.get(raw[i]);
            } catch (NoLocalizedTextException e) {
                translated[i] = raw[i];
            }
        }
        return translated;
    }

    public static String[] getLocaleCodes() {
        Localizer lizer = Localization.getGlobalLocalizerAdvanced();
        String[] rawLocales = lizer.getAvailableLocales();
        return removeDefault(rawLocales);
    }

    public static String[] getLocaleNames() {
        String[] rawDefaultRemoved = getLocaleCodes();
        return translateLocales(rawDefaultRemoved);
    }

    /**
     * Creates new context with updated layout configuration based on locale from {@link Localization#getCurrentLocale}.
     *
     * @param context old context.
     * @return updated context.
     */
    public static Context setLocale(Context context) {
        try {
            if (BuildConfig.DEBUG)
                return updateResources(context, Localization.getCurrentLocale().equals("hin") ? "ar" : Localization.getCurrentLocale());
            else
                return updateResources(context, Localization.getCurrentLocale());
        } catch (Exception ignored) {
        }
        return context;
    }

    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= 17) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
//            res.updateConfiguration(config, res.getDisplayMetrics());
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        return context;
    }

    public static Locale getLocale(Resources res) {
        Configuration config = res.getConfiguration();
        return Build.VERSION.SDK_INT >= 24 ? config.getLocales().get(0) : config.locale;
    }

}
