package org.commcare.views.extensions

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat

/**
 * Resolves a colour role declared on the theme, so code can name a role the way a layout's `?attr/`
 * does. Handles a role assigned either a colour resource or a literal colour.
 *
 * Throws when the theme does not declare [attr]: an unresolved role would otherwise resolve to a
 * transparent colour and go unnoticed until someone looked at the screen.
 */
fun Context.themeColor(
    @AttrRes attr: Int,
): Int {
    val value = TypedValue()
    require(theme.resolveAttribute(attr, value, true)) {
        "${resources.getResourceName(attr)} is not declared on this theme"
    }
    return if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
}
