package org.commcare.fragments.extensions

import androidx.fragment.app.Fragment

fun Fragment.hasLiveView(): Boolean = isAdded && view != null
