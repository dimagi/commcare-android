package org.commcare.utils

import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed

/**
 * A utility view interaction to check whether a view is present in the screen or not.
 * Espresso APIs are designed away from conditional logic by only allowing test actions and assertions.
 * So it's kinda against what espresso tells you to do.
 */
fun ViewInteraction.isPresent(): Boolean {
    return try {
        check(matches(isDisplayed()))
        true
    } catch (e: NoMatchingViewException) {
        false
    }
}