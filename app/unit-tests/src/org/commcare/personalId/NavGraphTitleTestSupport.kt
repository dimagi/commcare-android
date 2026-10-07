package org.commcare.personalId

import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.annotation.NavigationRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.commcare.dalvik.R
import org.hamcrest.Matchers.allOf
import org.xmlpull.v1.XmlPullParser

data class TitledScreen(
    @IdRes val destinationId: Int,
    @StringRes val titleRes: Int,
    val args: Bundle? = null,
)

data class NavAction(
    @IdRes val sourceId: Int,
    @IdRes val actionId: Int,
    @IdRes val destinationId: Int,
)

object NavGraphTitleTestSupport {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"

    fun assertToolbarTitle(
        @StringRes titleRes: Int,
    ) {
        onView(allOf(withText(titleRes), isDescendantOfA(withId(R.id.toolbar))))
            .check(matches(isDisplayed()))
    }

    fun getFragmentToFragmentActions(
        context: Context,
        @NavigationRes graphRes: Int,
    ): Set<NavAction> {
        val actions = mutableListOf<NavAction>()
        val dialogIds = mutableSetOf<Int>()
        var sourceId: Int? = null
        context.resources.getXml(graphRes).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "fragment" -> {
                        sourceId = parser.idAttribute()
                    }

                    "dialog" -> {
                        sourceId = null
                        dialogIds += parser.idAttribute()
                    }

                    "action" -> {
                        sourceId?.let {
                            actions +=
                                NavAction(
                                    sourceId = it,
                                    actionId = parser.idAttribute(),
                                    destinationId = parser.getAttributeResourceValue(APP_NS, "destination", 0),
                                )
                        }
                    }
                }
            }
        }
        return actions
            .filter { it.destinationId != it.sourceId && it.destinationId !in dialogIds }
            .toSet()
    }

    fun getResourceNameForId(
        context: Context,
        @IdRes id: Int,
    ): String = context.resources.getResourceEntryName(id)

    private fun XmlResourceParser.idAttribute(): Int = getAttributeResourceValue(ANDROID_NS, "id", 0)
}
