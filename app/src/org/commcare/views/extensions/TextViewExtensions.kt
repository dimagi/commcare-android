package org.commcare.views.extensions

import android.view.View
import android.widget.TextView

/**
 * Binds optional [text] to this view: sets the text and shows the view when [text] is non-empty.
 * When [text] is null or empty the view takes [absentVisibility], which defaults to [View.GONE].
 */
fun TextView.bindOptional(
    text: CharSequence?,
    absentVisibility: Int = View.GONE,
) {
    this.text = text
    visibility = if (text.isNullOrEmpty()) absentVisibility else View.VISIBLE
}

/**
 * As [bindOptional], except an absent [text] leaves the view [View.INVISIBLE] rather than gone, so
 * its line keeps occupying space. Use it to keep sibling views the same height whether or not their
 * optional lines are populated.
 */
fun TextView.bindReservingSpace(text: CharSequence?) = bindOptional(text, View.INVISIBLE)
