package org.commcare.utils

import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers
import junit.framework.Assert
import org.commcare.dalvik.R


/**
 * A workaround to Failed resolution of: Lkotlin/_Assertions;
 * This will fail the test if the value is false.
 */
fun assert(value: Boolean, failMsg: String) {
    if (!value) {
        Assert.fail("Assertion Failed: $failMsg")
    }
}

/**
 * A utility to pressBack until Home screen is reached at most 6 times.
 */
fun gotoHome() {
    for (i in 0..5) { // Try atmost 6 times.
        if (Espresso.onView(ViewMatchers.withId(R.id.home_gridview_buttons)).isPresent()) {
            return
        } else {
            Espresso.pressBack()
        }
    }
}
