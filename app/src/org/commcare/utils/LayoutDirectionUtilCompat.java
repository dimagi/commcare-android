package org.commcare.utils;

import android.os.Build;
import android.view.View;

import org.commcare.preferences.MainConfigurablePreferences;

/**
 * Used for updating layout direction, setting it to either {@link android.view.View#LAYOUT_DIRECTION_RTL} or {@link android.view.View#LAYOUT_DIRECTION_LTR}.
 */
public class LayoutDirectionUtil {
    private LayoutDirectionUtil(){}

    /**
     *
     * Updates layout direction depending on currently set locale.
     * If {@link MainConfigurablePreferences#isLocaleRTL()} returns <code>TRUE</code>,
     * it will set all views/layouts to {@link View#LAYOUT_DIRECTION_RTL}.
     * @param view view to be updated.
     * @param isRTL 
     */
    public static void updateLayoutDirection(View view, boolean isRTL) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (MainConfigurablePreferences.isLocaleRTL()) {
                view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            } else {
                view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }
        }
    }
}
