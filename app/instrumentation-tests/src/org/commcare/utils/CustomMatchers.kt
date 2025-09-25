package org.commcare.utils

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.espresso.intent.Checks
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.util.TreeIterables
import com.mapbox.mapboxsdk.plugins.annotation.Line
import org.commcare.android.database.global.models.AppAvailableToInstall
import org.hamcrest.*


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

    /**
     * Creates a matcher that matches an intent with specified action, category and type.
     */
    fun withIntent(action: String, category: String, type: String): Matcher<Intent> {
        return object : TypeSafeMatcher<Intent>() {
            override fun describeTo(description: Description) {
                description.appendText("has action: $action, category: $category and type: $type")
            }

            override fun matchesSafely(intent: Intent): Boolean {
                val actionMatcher = Matchers.`is`(action).matches(intent.action)
                val categoryMatcher = Matchers.hasItem(category).matches(intent.categories)
                val typeMatcher = Matchers.`is`(type).matches(intent.type)
                return actionMatcher && categoryMatcher && typeMatcher
            }
        }
    }

    fun isPasswordHidden(): Matcher<View?>? {
        return object : BoundedMatcher<View?, EditText>(
            EditText::class.java
        ) {
            override fun describeTo(description: Description) {
                description.appendText("Password is hidden")
            }

            override fun matchesSafely(editText: EditText): Boolean {
                //returns true if password is hidden
                return editText.getTransformationMethod() is PasswordTransformationMethod
            }
        }
    }

    fun withTextColor(color: Int): Matcher<View?>? {
        Checks.checkNotNull(color)
        return object : BoundedMatcher<View?, TextView>(
            TextView::class.java
        ) {
            override fun matchesSafely(row: TextView): Boolean {
                return color == row.currentTextColor
            }

            override fun describeTo(description: Description) {
                description.appendText("with text color: ")
            }
        }
    }

    fun withImageBgColor(color: Int): Matcher<View?>? {
        Checks.checkNotNull(color)
        return object : BoundedMatcher<View?, ImageView>(
            ImageView::class.java
        ) {
            override fun matchesSafely(row: ImageView): Boolean {
                return color == (row.getBackground() as ColorDrawable).getColor()
            }

            override fun describeTo(description: Description) {
                description.appendText("with text color: ")
            }
        }
    }

}
