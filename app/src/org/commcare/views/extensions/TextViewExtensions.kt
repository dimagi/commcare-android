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

/**
 * As [bindOptional], except an absent [text] leaves the view [View.INVISIBLE] rather than gone, so
 * its line keeps occupying space. Use it to keep sibling views the same height whether or not their
 * optional lines are populated.
 */
fun TextView.bindReservingSpace(text: CharSequence?) {
    this.text = text
    visibility = if (text.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
}
