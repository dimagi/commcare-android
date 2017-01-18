package org.commcare.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.text.Spannable;
import android.util.Pair;

import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * @author ctsims
 */
public class StringUtils {

    //TODO: Bro you can't just cache every fucking string ever.
    private static LruCache<String, String> normalizationCache;

    private static Pattern diacritics;

    //TODO: Really not sure about this size. Also, the LRU probably isn't really the best model here
    //since we'd _like_ for these caches to get cleaned up at _some_ point.
    static final private int cacheSize = 100 * 1024;

    /**
     * @param input A non-null string
     * @return a canonical version of the passed in string that is lower cased and has removed diacritical marks
     * like accents.
     */
    @SuppressLint("NewApi")
    public synchronized static String normalize(String input) {
        if (normalizationCache == null) {
            normalizationCache = new LruCache<>(cacheSize);

            diacritics = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        }
        String cachedString = normalizationCache.get(input);
        if (cachedString != null) {
            return cachedString;
        }

        //Initialized the normalized string (If we can, we'll use the Normalizer API on it)
        String normalized = input;

        //If we're above gingerbread we'll normalize this in NFD form 
        //which helps a lot. Otherwise we won't be able to clear up some of those
        //issues, but we can at least still eliminate diacritics.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        } else {
            //TODO: I doubt it's worth it, but in theory we could run
            //some other normalization for the minority of pre-API9
            //devices.
        }

        String output = diacritics.matcher(normalized).replaceAll("").toLowerCase();

        normalizationCache.put(input, output);

        return output;
    }

    public static String getStringRobust(Context c, int resId) {
        return getStringRobust(c, resId, "");
    }

    public static String getStringRobust(Context c, int resId, String args) {
        String resourceName = c.getResources().getResourceEntryName(resId);
        try {
            return Localization.get("odk_" + resourceName, new String[]{args});
        } catch (NoLocalizedTextException e) {
            return c.getString(resId, args);
        }
    }

    public static String getStringRobust(Context c, int resId, @NonNull String[] args) {
        String resourceName = c.getResources().getResourceEntryName(resId);
        try {
            return Localization.get("odk_" + resourceName, args);
        } catch (NoLocalizedTextException e) {
            return c.getString(resId, args);
        }
    }

    public static Spannable getStringSpannableRobust(Context c, int resId) {
        return getStringSpannableRobust(c, resId, "");
    }

    public static Spannable getStringSpannableRobust(Context c, int resId, String args) {
        String resourceName = c.getResources().getResourceEntryName(resId);
        String ret = "";
        try {
            ret = Localization.get("odk_" + resourceName, new String[]{args});
        } catch (NoLocalizedTextException e) {
            ret = c.getString(resId, args);
        }
        return MarkupUtil.styleSpannable(c, ret);
    }
}
