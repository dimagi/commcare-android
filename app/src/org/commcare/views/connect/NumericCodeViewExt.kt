package org.commcare.views.connect

import android.widget.ImageView
import org.commcare.dalvik.R

/**
 * Binds a given [ImageView] as visibility toggle for the [NumericCodeView]
 */
fun NumericCodeView.bindVisibilityToggle(toggle: ImageView) {
    toggle.setOnClickListener {
        isPasswordVisible = !isPasswordVisible
        toggle.setImageResource(
            if (isPasswordVisible) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24,
        )
    }
}
