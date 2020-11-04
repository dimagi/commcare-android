package org.commcare.androidTests

import android.os.Build
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.RequiresDevice
import androidx.test.filters.SdkSuppress
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.commcare.annotations.WifiDisabled
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class FormEntryTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "languages.ccz"
        const val APP_NAME = "Language Test"
    }

    @Before
    fun setup() {
        installApp(LanguagesTest.APP_NAME, LanguagesTest.CCZ_NAME, true)
    }

    @Test
    fun testIncompleteFormCreation() {
        InstrumentationUtility.login("user_with_no_data", "123")
        // Create an incomplete form.
        InstrumentationUtility.openForm(0, 0)
        saveAsIncomplete()

        // Open the incomplete form and make changes but do not save.
        InstrumentationUtility.openFirstIncompleteForm()
        onView(withId(R.id.jumpBeginningButton))
                .perform(click())
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("test"))
        closeSoftKeyboard()
        Espresso.pressBack()
        onView(withText(R.string.do_not_save))
                .perform(click())
        InstrumentationUtility.gotoHome()

        // Open the incomplete form and confirm that the changes you made aren't saved.
        InstrumentationUtility.openFirstIncompleteForm()
        onView(withId(R.id.jumpBeginningButton))
                .perform(click())
        onView(withClassName(endsWith("EditText")))
                .check(matches(withText("")))

        // Again make changes and this time save it.
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("test"))
        saveAsIncomplete()

        // Open incomplete form again and confirm that the changes you made exists.
        InstrumentationUtility.openFirstIncompleteForm()
        onView(withId(R.id.jumpBeginningButton))
                .perform(click())
        onView(withClassName(endsWith("EditText")))
                .check(matches(withText("test")))

        // Confirm that we can submit the form.
        onView(withId(R.id.nav_btn_finish))
                .perform(click())

        // Check that the form now appears in saved form and not in incomplete form.
        onView(withText("Saved"))
                .perform(click())
        onView(withText("Languages"))
                .check(matches(isDisplayed()))
        Espresso.pressBack()
        onView(withText(startsWith("Incomplete")))
                .perform(click())
        onView(withText("Languages"))
                .check(doesNotExist())
    }

    @Test
    fun testSaveFormMenu() {
        InstrumentationUtility.login("user_with_no_data", "123")
        // Create an incomplete form.
        InstrumentationUtility.openForm(0, 0)
        closeSoftKeyboard()

        // Confirm that backing out without saving goes to form list.
        Espresso.pressBack()
        onView(withText(R.string.do_not_save))
                .perform(click())
        onView(CustomMatchers.find(
                allOf(withText("Basic Form Tests")),
                1
        )).check(matches(isDisplayed()))
        onView(withText("Languages"))
                .perform(click())

        // Make changes to the form.
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("test"))
        closeSoftKeyboard()

        // Save the form using options menu item.
        InstrumentationUtility.openOptionsMenu()
        onView(withText(R.string.save_all_answers))
                .perform(click())

        // Exit form using do not save.
        Espresso.pressBack()
        onView(withText(R.string.do_not_save))
                .perform(click())
        InstrumentationUtility.gotoHome()

        // Open the incomplete form and confirm that the changes exists.
        InstrumentationUtility.openFirstIncompleteForm()
        onView(withId(R.id.jumpBeginningButton))
                .perform(click())
        onView(withClassName(endsWith("EditText")))
                .check(matches(withText("test")))
    }

    @Test
    fun testFormEntryQuirks() {
        InstrumentationUtility.login("user_with_no_data", "123")
        InstrumentationUtility.openForm(0, 1)

        // Trigger constraint violation(require response)
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withText("Sorry, this response is required!"))
                .check(matches(isDisplayed()))

        // Confirm that we can save form despite violated constraint
        saveAsIncomplete()
        onView(withText(startsWith("Incomplete")))
                .perform(click())
        onView(withText("Constraint"))
                .check(matches(isDisplayed()))
    }

    @RequiresDevice
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
    @Test
    @WifiDisabled
    fun testSync() {
        InstrumentationUtility.login("user_with_no_data", "123")
        InstrumentationUtility.logout()
        // Disable wifi
        InstrumentationUtility.changeWifi(false)
        // We can still login.
        InstrumentationUtility.login("user_with_no_data", "123")
        // Submit a form.
        InstrumentationUtility.openForm(0, 0)
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("hello"))
        onView(withId(R.id.nav_btn_finish))
                .perform(click())
        // Confirm unsent form.
        onView(withText("Start"))
                .perform(click())
        Espresso.pressBack()
        onView(withText("Unsent Forms: 1"))
                .check(matches(isDisplayed()))

        // Enabled wifi.
        InstrumentationUtility.changeWifi(true)

        // Confirm form is sent on sync.
        onView(withText("Sync with Server"))
                .perform(click())
        onView(withText("Unsent Forms: 1"))
                .check(doesNotExist())
        onView(withText("Start"))
                .perform(click())
        Espresso.pressBack()
        onView(withText(startsWith("You last synced with the server:")))
                .check(matches(isDisplayed()))

        // Confirm form is present in saved forms
        onView(withText("Saved"))
                .perform(click())
        onView(withText("Languages"))
                .check(matches(isDisplayed()))
        InstrumentationUtility.logout()
    }

    @Test
    fun testSaveCase() {
        InstrumentationUtility.login("form_tests", "123")
        // Create incomplete update case form.
        InstrumentationUtility.openForm(1, 1)
        openCase("Snow")
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("hello"))
        saveAsIncomplete()

        // testing notification for having incomplete form for case already made
        InstrumentationUtility.openForm(1, 1)
        openCase("Snow")
        confirmNotification_whenCaseHasIncompeleteForm()
        onView(withText("NO"))
                .perform(click())
        onView(withText("A"))
                .perform(click())
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withClassName(endsWith("EditText")))
                .check(matches(withText(""))) // we don't see the text hello here.
        saveAsIncomplete()

        // Deleting one incomplete form for case
        onView(withText(startsWith("Incomplete")))
                .perform(click())
        onView(CustomMatchers.find(
                allOf(withText("Update a Case")),
                1
        )).perform(longClick())
        onView(withText("Open"))
                .check(matches(isDisplayed()))
        onView(withText("Delete Record"))
                .check(matches(isDisplayed()))
        onView(withText("Scan Record Integrity"))
                .check(matches(isDisplayed()))
        onView(withText("Delete Record"))
                .perform(click())

        // Confirm we still have one and only one incomplete case.
        onView(withText("Update a Case"))
                .check(matches(isDisplayed()))
        Espresso.pressBack()

        // Continue incomplete case form.
        InstrumentationUtility.openForm(1, 1)
        openCase("Snow")
        confirmNotification_whenCaseHasIncompeleteForm()
        onView(withText("YES"))
                .perform(click())
        onView(withId(R.id.jumpEndButton))
                .perform(click())

        // Save form
        onView(withText("hello"))
                .check(matches(isDisplayed()))
        saveAsIncomplete()

        // Confirm that we have only one incomplete form.
        onView(withText(startsWith("Incomplete")))
                .perform(click())
        onView(withText("Update a Case"))
                .check(matches(isDisplayed()))
    }

    private fun openCase(caseName: String) {
        onView(withText(caseName))
                .perform(click())
        onView(withText("Continue"))
                .perform(click())
    }

    private fun saveAsIncomplete() {
        closeSoftKeyboard()
        Espresso.pressBack()
        onView(withText(R.string.keep_changes))
                .perform(click())
    }

    private fun confirmNotification_whenCaseHasIncompeleteForm() {
        onView(withText("Continue Form"))
                .check(matches(isDisplayed()))
        onView(withText("DELETE OLD COPY"))
                .check(matches(isDisplayed()))
        onView(withText("YES"))
                .check(matches(isDisplayed()))
        onView(withText("NO"))
                .check(matches(isDisplayed()))
    }

}
