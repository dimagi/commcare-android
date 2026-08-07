package org.commcare.views.extensions

import android.view.View
import android.widget.TextView

/**
 * Binds optional [text] to this view: sets the text and shows the view when [text] is non-empty,
 * or hides it ([View.GONE]) when [text] is null or empty.
 */
fun TextView.bindOptional(text: CharSequence?) {
    this.text = text
    visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
}
