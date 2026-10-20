package org.commcare.views.extensions

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

/** Invokes [onChanged] with the current text after every change to this field. */
fun EditText.onTextChanged(onChanged: (String) -> Unit) {
    addTextChangedListener(
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {}

            override fun afterTextChanged(s: Editable?) {
                onChanged(s?.toString().orEmpty())
            }
        },
    )
}
