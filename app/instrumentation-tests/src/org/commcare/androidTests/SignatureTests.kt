package org.commcare.androidTests

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.annotations.BrowserstackTests
import org.commcare.utils.InstrumentationUtility
import org.commcare.views.DrawView
import org.commcare.dalvik.R
import org.commcare.utils.doesNotExist
import org.commcare.utils.isDisplayed
import org.hamcrest.Matchers
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author $|-|!˅@M
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class SignatureTests: BaseTest() {
    companion object {
        const val CCZ_NAME = "signature_tests.ccz"
        const val APP_NAME = "SignatureTests"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test_signature_user", "123")
    }

    @Test
    fun testCannotSaveBlankSignatures() {
        // Open signature capture activity
        InstrumentationUtility.openForm(0, 0)
        onView(withText(R.string.sign_button))
                .perform(click())

        // Saving should be disabled by default.
        assertSavingNotPossible()

        // Draw a line should enable saving
        onView(instanceOf(DrawView::class.java))
                .perform(GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT, GeneralLocation.TOP_RIGHT, Press.FINGER))
        assertSavingPossible()

        // Reset should disable saving again
        onView(withId(R.id.btnResetDraw))
                .perform(click())
        assertSavingNotPossible()

        // Draw a line and save.
        onView(instanceOf(DrawView::class.java))
                .perform(GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT, GeneralLocation.TOP_RIGHT, Press.FINGER))
        onView(withId(R.id.btnFinishDraw))
                .perform(click())

        // Confirm we auto-advance
        withText(R.string.sign_button).doesNotExist()
        withText("Done").isDisplayed()

        // Press back to signature widget
        onView(withId(R.id.nav_btn_prev))
                .perform(click())

        // Open draw activity again
        onView(withText(R.string.sign_button))
                .perform(click())

        // Saving is enabled because we already have something in canvas
        assertSavingPossible()

        // Reset should disable saving again
        onView(withId(R.id.btnResetDraw))
                .perform(click())
        assertSavingNotPossible()

        // Cancel shouldn't auto advance
        onView(withId(R.id.btnCancelDraw))
                .perform(click())
        withText(R.string.sign_button).isDisplayed()

        // Open draw again.
        onView(withText(R.string.sign_button))
                .perform(click())
        assertSavingPossible()

        //Reset and draw another line
        onView(withId(R.id.btnResetDraw))
                .perform(click())
        assertSavingNotPossible()
        onView(instanceOf(DrawView::class.java))
                .perform(GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT, GeneralLocation.TOP_RIGHT, Press.FINGER))
        assertSavingPossible()

        // Save using back button
        Espresso.pressBack()
        onView(withText(R.string.keep_changes))
                .perform(click())

        // Should auto-advance
        withText(R.string.sign_button).doesNotExist()
        withText("Done").isDisplayed()
    }

    /**
     * Asserts 2 things:
     * - Save And Close button should be disabled
     * - Save Incomplete button shouldn't be present in the back dialog
     */
    private fun assertSavingNotPossible() {
        onView(withId(R.id.btnFinishDraw))
                .check(matches(not(isEnabled())))
        Espresso.pressBack()
        withText(R.string.keep_changes).doesNotExist()
        Espresso.pressBack()
    }

    /**
     * Asserts 2 things:
     * - Save And Close button should be enabled
     * - Save Incomplete button should be visible in the back dialog
     */
    private fun assertSavingPossible() {
        onView(withId(R.id.btnFinishDraw))
                .check(matches(isEnabled()))
        Espresso.pressBack()
        withText(R.string.keep_changes).isDisplayed()
        Espresso.pressBack()
    }
}
