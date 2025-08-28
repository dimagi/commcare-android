package org.commcare.utils;

import android.content.Context;
import android.text.Spannable;

import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import androidx.annotation.NonNull;

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

    public static String getLocalizedLevel(String levelCode, Context context) {
        return switch (levelCode) {
            case "1MON_ACTIVE" -> context.getString(R.string.personalid_credential_level_1_month_active);
            case "2MON_ACTIVE" -> context.getString(R.string.personalid_credential_level_2_month_active);
            case "3MON_ACTIVE" -> context.getString(R.string.personalid_credential_level_3_month_active);
            case "6MON_ACTIVE" -> context.getString(R.string.personalid_credential_level_6_month_active);
            case "9MON_ACTIVE" -> context.getString(R.string.personalid_credential_level_9_month_active);
            case "12MON_ACTIVE" -> context.getString(R.string.personalid_credential_level_12_month_active);
            default -> levelCode;
        };
    }
}
