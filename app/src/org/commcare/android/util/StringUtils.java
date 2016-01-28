package org.commcare.android.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
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

    /**
     * Computes the Levenshtein Distance between two strings.
     * <p/>
     * This code is sourced and unmodified from wikibooks under
     * the Creative Commons attribution share-alike 3.0 license and
     * by be re-used under the terms of that license.
     * <p/>
     * http://creativecommons.org/licenses/by-sa/3.0/
     * <p/>
     * TODO: re-implement for efficiency/licensing possibly.
     */
    private static int LevenshteinDistance(String s0, String s1) {
        int len0 = s0.length() + 1;
        int len1 = s1.length() + 1;

        // the array of distances
        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        // initial cost of skipping prefix in String s0
        for (int i = 0; i < len0; i++) cost[i] = i;

        // dynamicaly computing the array of distances

        // transformation cost for each letter in s1
        for (int j = 1; j < len1; j++) {

            // initial cost of skipping prefix in String s1
            newcost[0] = j - 1;

            // transformation cost for each letter in s0
            for (int i = 1; i < len0; i++) {

                // matching current letters in both strings
                int match = (s0.charAt(i - 1) == s1.charAt(j - 1)) ? 0 : 1;

                // computing cost for each transformation
                int cost_replace = cost[i - 1] + match;
                int cost_insert = cost[i] + 1;
                int cost_delete = newcost[i - 1] + 1;

                // keep minimum cost
                newcost[i] = Math.min(Math.min(cost_insert, cost_delete), cost_replace);
            }

            // swap cost/newcost arrays
            int[] swap = cost;
            cost = newcost;
            newcost = swap;
        }

        // the distance is the cost for transforming all letters in both strings
        return cost[len0 - 1];
    }

    /**
     * Identifies whether two strings are close enough that they are likely to be
     * intended to be the same string. Fuzzy matching is only performed on strings that are
     * longer than a certain size.
     *
     * @return A pair with two values. First value represents a match: true if the two strings
     * meet CommCare's fuzzy match definition, false otherwise. Second value is the actual string
     * distance that was matched, in order to be able to rank or otherwise interpret results.
     */
    public static Pair<Boolean, Integer> fuzzyMatch(String source, String target) {
        //tweakable parameter: Minimum length before edit distance
        //starts being used (this is probably not necessary, and
        //basically only makes sure that "at" doesn't match "or" or similar
        if (source.length() > 3) {
            int distance = StringUtils.LevenshteinDistance(source, target);
            //tweakable parameter: edit distance past string length disparity
            if (distance <= 2) {
                return Pair.create(true, distance);
            }
        }
        return Pair.create(false, -1);
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

    public static String getStringRobust(Context c, int resId, String[] args) {
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
