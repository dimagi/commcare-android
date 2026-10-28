package org.commcare.utils

import android.content.res.XmlResourceParser

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

fun XmlResourceParser.getAndroidIdAttribute(): Int = getAttributeResourceValue(ANDROID_NS, "id", 0)
