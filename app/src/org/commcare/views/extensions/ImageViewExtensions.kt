package org.commcare.views.extensions

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.widget.ImageViewCompat

/** Tints this view's drawable with a single color. */
fun ImageView.tint(
    @ColorInt color: Int,
) = ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
