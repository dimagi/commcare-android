package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.doesNotExist
import org.commcare.utils.isNotDisplayed
import org.commcare.utils.isDisplayed
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author $|-|!˅@M
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class ClearButtonTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "clear_button_test.ccz"
        const val APP_NAME = "ClearButton"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("user_with_no_data", "123")
    }

    @Test
    fun testClearButton() {
        // Open the only form in the app.
        InstrumentationUtility.openForm(0, 0)
        withText("Select One Widget tests").isDisplayed()

        // Check clear button works with radio group.
        // Make sure the clear button isn't displayed until we make a selection.
        withText("Clear").isNotDisplayed()
        onView(withText("First choice"))
                .perform(click())
        withText("Clear").isDisplayed()

        onView(withText("First choice"))
                .check(matches(isChecked()))

        // Make sure clicking the clear button removes the selection and also hides the clear button.
        onView(withText("Clear"))
                .perform(click())
        onView(withText("First choice"))
                .check(matches(isNotChecked()))
        withText("Clear").isNotDisplayed()

        // Go to next question. and confirm clear button doesn't exists for checkboxes.
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        withText("Select Multi Widget.").isDisplayed()
        withText("Clear").doesNotExist()
        // Select a choice
        onView(withText("First"))
                .perform(click())
        // Clear button is still not visible.
        withText("Clear").doesNotExist()

        // Even if we select all the choices, it isn't visible.
        onView(withText("Second"))
                .perform(click())
        withText("Clear").doesNotExist()


        // Check clear button with AutoAdvance Radio group.
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        withText("Select One Auto Advanced").isDisplayed()
        withText("Clear").isNotDisplayed()
        // selecting any choice automatically moves user to next question.
        onView(withText("first"))
                .perform(click())

        withText("Select One Auto Advanced").doesNotExist()
        withText("Should be automatically visible").isDisplayed()

        // Go back and see that clear button is visible now.
        onView(withId(R.id.nav_btn_prev))
                .perform(click())
        withText("Clear").isDisplayed()
        onView(withText("first"))
                .check(matches(isChecked()))

        // Clicking the clear button should remove the selection and shoudn't auto advance to next question.
        onView(withText("Clear"))
                .perform(click())
        onView(withText("first"))
                .check(matches(isNotChecked()))
        withText("Clear").isNotDisplayed()
        withText("Select One Auto Advanced").isDisplayed()

        // Again selection a choice should move to next question.
        onView(withText("first"))
                .perform(click())
        withText("Select One Auto Advanced").doesNotExist()
        withText("Should be automatically visible").isDisplayed()

        // We should be able to submit the form.
        onView(withId(R.id.nav_btn_finish))
                .perform(click())
    }

}
