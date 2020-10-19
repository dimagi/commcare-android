package org.commcare.androidTests

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.CommCareApplication
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LanguagesTest : BaseTest() {

    companion object {
        const val CCZ_NAME = "languages.ccz"
    }

    @Before
    fun setup() {
        if (CommCareApplication.instance().currentApp == null) {
            InstrumentationUtility.installApp(FormEntryTest.CCZ_NAME)
        } else {
            InstrumentationUtility.uninstallCurrentApp()
            InstrumentationUtility.installApp(FormEntryTest.CCZ_NAME)
            Espresso.pressBack()
        }
        InstrumentationUtility.login("user_with_no_data", "123")
    }

    @Test
    fun testLanguageChanges() {
        InstrumentationUtility.openForm(0, 0)
        onView(withText("Enter a name:"))
                .check(matches(isDisplayed()))
        exitForm()
        InstrumentationUtility.gotoHome()
        selectLanguageChangeOption()
        checkLanguageOptions()

        // Confirm the options persist on rotation
        InstrumentationUtility.rotateLeft()
        checkLanguageOptions()
        InstrumentationUtility.rotatePortrait()
        checkLanguageOptions()

        // Clicking just after rotating doesn't work for some reason, so waiting here for a moment.
        InstrumentationUtility.sleep(2)
        onView(withText("Hindi"))
                .perform(click())
        onView(withText("Start"))
                .perform(click())
        onView(withText("Basic Form Tests"))
                .perform(click())
        onView(withText("HIN: Languages"))
                .perform(click())

        onView(withText("HIN: Enter a name:"))
                .check(matches(isDisplayed()))

        // Check form language changes
        selectLanguageChangeOption()
        checkLanguageOptions()
        onView(withText("English"))
                .perform(click())

        onView(withText("HIN: Enter a name:"))
                .check(doesNotExist())
        onView(withText("Enter a name:"))
                .check(matches(isDisplayed()))

        // Confirm app language remains the same.
        exitForm()
        onView(withText("HIN: Languages"))
                .check(matches(isDisplayed()))
    }

    @Test
    fun testAppUpdate_dontInterfereLanguageChanges() {
        selectLanguageChangeOption()
        checkLanguageOptions()
        // Change language to hindi
        onView(withText("Hindi"))
                .perform(click())

        // Update app
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Update App"))
                .perform(click())
        onView(withText("Update to version 103 & log out"))
                .perform(click())
        InstrumentationUtility.login("user_with_no_data", "123")

        // Confirm app language remains hindi
        onView(withText("Start"))
                .perform(click())
        onView(withText("Basic Form Tests"))
                .perform(click())
        onView(withText("HIN: Languages"))
                .check(matches(isDisplayed()))
    }

    private fun selectLanguageChangeOption() {
        InstrumentationUtility.openOptionsMenu()
        onView(withText(R.string.change_language))
                .perform(click())
    }

    private fun exitForm() {
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        onView(withText(R.string.do_not_save))
                .perform(click())
    }

    private fun checkLanguageOptions() {
        // We see 2 choices::
        onView(withId(R.id.choices_list_view))
                .check(matches(CustomMatchers.matchListSize(2)))
        // English and Hindi
        onView(withText("English"))
                .check(matches(isDisplayed()))
        onView(withText("Hindi"))
                .check(matches(isDisplayed()))
    }
}