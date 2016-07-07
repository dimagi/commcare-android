package org.commcare.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;

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
    @SuppressLint("NewApi")
    public static int generateViewId() {
        //raw implementation for < API 17
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            for (; ; ) {
                final int result = sNextGeneratedId.get();
                // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
                int newValue = result + 1;
                if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
                if (sNextGeneratedId.compareAndSet(result, newValue)) {
                    return result;
                }
            }
        } else {
            //Whatever the current implementation is otherwise
            return View.generateViewId();
        }
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
}
