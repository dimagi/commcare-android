package org.commcare.utils

import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.util.TreeIterables
import org.commcare.android.database.global.models.AppAvailableToInstall
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher


/**
 * I would've created a Matchers+Extension file but as of now
 * kotlin doesn't allow adding static extension funtions to a java class.
 * https://youtrack.jetbrains.com/issue/KT-11968
 */
object CustomMatchers {

    /**
     * Creates a matcher that matches whether the appName is present in the app installation list.
     */
    @JvmStatic
    fun withAppName(appName: String): TypeSafeMatcher<AppAvailableToInstall> {
        return object: TypeSafeMatcher<AppAvailableToInstall>() {
            override fun describeTo(description: Description) {
                description.appendText("will match if $appName is present in the App List")
            }

            override fun matchesSafely(item: AppAvailableToInstall): Boolean {
                return item.appName == appName
            }

        }
    }

    /**
     * Wraps an existing matcher, to find the n-th matched item.
     * In case a view contains more than 1 items of same type,
     * use this to select the item at <code>position</code>.
     * NOTE:- position is 1 based.
     */
    @JvmStatic
    fun <T> find(matcher: Matcher<T>, position: Int): Matcher<T> {
        return object : BaseMatcher<T>() {
            var count = 0
            override fun matches(item: Any): Boolean {
                if (matcher.matches(item)) {
                    count++
                    return count == position
                }
                return false
            }

            override fun describeTo(description: Description) {
                description.appendText("will return $position matching item")
            }
        }
    }

    /**
     * Creates a matcher that matches the number of items in the list to the specified <code>size</code>
     */
    @JvmStatic
    fun matchListSize(size: Int): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            override fun matchesSafely(view: View): Boolean {
                return if (view is ListView) {
                    view.adapter.count == size
                } else {
                    throw IllegalStateException("matchListSize() should only be used with " +
                            "listView. Current view is :: " + view.javaClass.simpleName)
                }
            }

            override fun describeTo(description: Description) {
                description.appendText("will match listView item size with $size")
            }
        }
    }

    /**
     * Creates a matcher that wraps another matcher and counts all the childrens of a viewgroup
     * that matches the wrapped <code>childMatcher</code>.
     * And asserts that count with the specified <code>count</code>
     */
    @JvmStatic
    fun withChildViewCount(count: Int, childMatcher: Matcher<View>): Matcher<View> {
        return object : BoundedMatcher<View, ViewGroup>(ViewGroup::class.java) {
            override fun matchesSafely(viewGroup: ViewGroup): Boolean {
                var matchCount = 0
                for (child in TreeIterables.breadthFirstViewTraversal(viewGroup)) {
                    if (childMatcher.matches(child)) {
                        matchCount++
                    }
                }
                return matchCount == count
            }

            override fun describeTo(description: Description) {
                description.appendText("ViewGroup with child-count=$count and")
                childMatcher.describeTo(description)
            }
        }
    }

    fun withFontSize(expectedSize: Float): Matcher<View> {
        return object : BoundedMatcher<View, View>(View::class.java) {
            override fun matchesSafely(target: View): Boolean {
                if (target !is TextView) {
                    return false
                }
                val pixels = target.textSize
                val actualSize = pixels / target.getResources().displayMetrics.scaledDensity
                return actualSize.compareTo(expectedSize) == 0
            }

            override fun describeTo(description: Description) {
                description.appendText("with match textView fontSize with $expectedSize")
                description.appendValue(expectedSize)
            }
        }
    }

}