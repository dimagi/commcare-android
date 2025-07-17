package org.commcare.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ctsims
 */
public class AndroidUtil {
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    /**
     * Generate a value suitable for use in setId(int).
     * This value will not collide with ID values generated at build time by aapt for R.id.
     *
     * @return a generated ID value
     */
    public static int generateViewId() {
        return View.generateViewId();
    }

    /**
     * Returns an int array with the color values for the given attributes (R.attr).
     * Any unresolved colors will be represented by -1
     */
    public static int[] getThemeColorIDs(final Context context, final int[] attrs) {
        int[] colors = new int[attrs.length];
        Resources.Theme theme = context.getTheme();
        for (int i = 0; i < attrs.length; i++) {
            TypedValue typedValue = new TypedValue();
            if (theme.resolveAttribute(attrs[i], typedValue, true)) {
                colors[i] = typedValue.data;
            } else {
                colors[i] = -1;
            }
        }
        return colors;
    }

    public static void showToast(Context context, int stringResource) {
        Toast.makeText(context,
                StringUtils.getStringRobust(
                        context,
                        stringResource),
                Toast.LENGTH_LONG).show();
    }

    public static HashMap<String, String> bundleAsMap(Bundle bundle) {
        HashMap<String, String> result = new HashMap<>();
        for (String key : bundle.keySet()) {
            result.put(key, bundle.getString(key));
        }
        return result;
    }

    public static boolean isGooglePlayServicesAvailable(Context ctx) {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS;
    }
}
