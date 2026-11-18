package org.commcare.utils

import android.os.Build
import androidx.core.view.WindowInsetsCompat

object SystemNavigationMode {
    /**
     * Returns whether the device uses gesture navigation, detected by the back-gesture areas the
     * system reserves along the left and right screen edges. Always false below Android 10, which
     * has no back gesture.
     */
    @JvmStatic
    fun isGestureNavigation(insets: WindowInsetsCompat?): Boolean {
        if (insets == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        val gestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
        val navigationBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        return gestureInsets.left > navigationBarInsets.left ||
            gestureInsets.right > navigationBarInsets.right
    }
}
