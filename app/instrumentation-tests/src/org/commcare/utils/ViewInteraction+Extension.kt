package org.commcare.utils

import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed

/**
 * A utility view interaction to check whether a view is present in the screen or not.
 * This method is same as <code>check(matches(isDisplayed()))</code> except that it doesn't throw
 * an exception if the view isn't displayed.
 */
fun ViewInteraction.isPresent(): Boolean {
    return try {
        check(matches(isDisplayed()))
        true
    } catch (e: NoMatchingViewException) {
        false
    }
}