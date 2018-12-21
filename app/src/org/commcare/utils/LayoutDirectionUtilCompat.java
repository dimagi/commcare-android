package org.commcare.utils;

import android.os.Build;
import android.view.View;

public class LayoutDirectionUtilCompat {

    private LayoutDirectionUtilCompat() {
    }

    /**
     * Updates view's layout direction by mirroring it.
     *
     * @param view  view to be updated.
     * @param isRTL
     */
    public static void mirrorView(View view, boolean isRTL) {
        if (isRTL) {
            view.setScaleX(-1f);
        } else {
            view.setScaleX(1f);
        }
    }

    /**
     * Updates view's layout direction by mirroring it. It will use view's context to determine if view should be mirrored.
     *
     * @param view view to be updated.
     */
    public static void mirrorView(View view) {
        mirrorView(view, Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                view.getContext().getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
    }
}
