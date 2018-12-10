package org.commcare.utils;

import android.os.Build;
import android.view.View;

public class LayoutDirectionUtilCompat {
    private LayoutDirectionUtilCompat() {
    }

    /**
     * Updates view's layout direction, setting it to either {@link android.view.View#LAYOUT_DIRECTION_RTL} or {@link android.view.View#LAYOUT_DIRECTION_LTR}.
     *
     * @param view  view to be updated.
     * @param isRTL
     */
    public static void updateLayoutDirection(View view, boolean isRTL) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (isRTL) {
                view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            } else {
                view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }
        }
    }
}
