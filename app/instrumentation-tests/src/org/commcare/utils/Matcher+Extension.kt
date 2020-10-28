package org.commcare.utils

import android.view.View
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import org.hamcrest.Matcher

/**
 * A utility to assert that a view is displayed.
 * Using this method you can replace <code> onView(withText("abc")).check(matches(isDisplayed())) </code>
 * with simply <code>withText("abc").isDisplayed()</code>
 */
fun Matcher<View>.isDisplayed() {
    Espresso.onView(this)
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
}

/**
 * A utility to assert that a view is not displayed.
 * Using this method you can replace <code> onView(withText("abc")).check(doesNotExist()) </code>
 * with simply <code>withText("abc").doesNotExist()</code>
 */
fun Matcher<View>.doesNotExist() {
    Espresso.onView(this)
            .check(ViewAssertions.doesNotExist())
}
