package org.commcare.utils;

import android.content.Context;
import android.text.Spannable;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;

import org.commcare.modern.util.Pair;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import java.io.Serializable;

/**
 * @author ctsims
 */
public class StringUtils {

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
