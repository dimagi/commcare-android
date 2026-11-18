package org.commcare.fragments.connectMessaging

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView

/**
 * The WebView a card runs in. It takes taps but never text (D29, spec section 6.6): it has no input
 * connection, cannot take focus, offers no text selection or paste, ignores dropped text, and is left
 * out of autofill so saved passwords are never offered to a card's form.
 */
@SuppressLint("ViewConstructor")
class ConnectMessageCardWebView(
    context: Context,
) : WebView(context) {
    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isLongClickable = false
        isHapticFeedbackEnabled = false
        setOnLongClickListener { true }
        setOnDragListener { _, _ -> true }
        overScrollMode = View.OVER_SCROLL_NEVER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? = null

    override fun onCheckIsTextEditor(): Boolean = false
}
