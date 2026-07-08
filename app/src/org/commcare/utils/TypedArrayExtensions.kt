@file:JvmName("TypedArrayExtensions") // This sets the generated class name

package org.commcare.utils

import android.content.res.TypedArray

/** Returns the float at [index] if the attribute is present, otherwise null. */
fun TypedArray.optionalFraction(index: Int): Float? = if (hasValue(index)) getFloat(index, 0f) else null
