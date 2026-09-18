package org.commcare.views.connect

import android.widget.ImageView
import org.commcare.dalvik.R

/**
 * Toggle visibility of a given view based on the visibility of the password
 */
fun NumericCodeView.toggleVisibility(toggle: ImageView) {
    isPasswordVisible = !isPasswordVisible
    toggle.setImageResource(
        if (isPasswordVisible) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24,
    )
}
