package org.commcare.androidTests

import android.os.Build
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import org.commcare.activities.*
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.hamcrest.Matchers.startsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MenuTests: BaseTest() {

    companion object {
        const val CCZ_NAME = "settings_sheet_tests.ccz"
        const val APP_NAME = "App for Settings Sheet"

        val homeMenuItems = arrayOf("Update App",
                "Saved Forms",
                "Change Language",
                "About CommCare",
                "Advanced",
                "Settings")

        val advancedOptions = arrayOf("Wifi Direct",
                "Manage SD",
                "Report Problem",
                "Force Log Submission",
                "Validate Media",
                "Connection Test",
                "Recovery Mode",
                "Clear User Data")
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("settings.test","123")
    }

    @Test
    @BrowserstackTests
    fun testHomeScreenOptions() {
        InstrumentationUtility.openOptionsMenu()
        checkStringExists(homeMenuItems)
        InstrumentationUtility.rotateLeft()
        checkStringExists(homeMenuItems)
        InstrumentationUtility.rotatePortrait()

        onView(withText(homeMenuItems[0]))
                .perform(click())
        Intents.intended(IntentMatchers.hasComponent(UpdateActivity::class.java.name))
        onView(withText("App is up to date"))
                .check(matches(isDisplayed()))
        InstrumentationUtility.gotoHome()

        InstrumentationUtility.openOptionsMenu()
        onView(withText(homeMenuItems[1]))
                .perform(click())
        Intents.intended(IntentMatchers.hasComponent(FormRecordListActivity::class.java.name))
        onView(withText(startsWith("Filter By:")))
                .perform(click())
        checkStringExists(arrayOf(
                "Filter By: All Completed Forms",
                "Filter By: Only Submitted Forms",
                "Filter By: Only Unsent Forms",
                "Only Incomplete Forms",
                "Filter: Quarantined Forms"))
        InstrumentationUtility.gotoHome()

        InstrumentationUtility.openOptionsMenu()
        onView(withText(homeMenuItems[2]))
                .perform(click())
        // Confirm we see 2 choices
        onView(withId(R.id.choices_list_view))
                .check(matches(CustomMatchers.matchListSize(2)))
        InstrumentationUtility.gotoHome()

        InstrumentationUtility.openOptionsMenu()
        onView(withText(homeMenuItems[3]))
                .perform(click())
        onView(withText("About CommCare"))
                .check(matches(isDisplayed()))
        onView(withText("OK"))
                .perform(click())
        InstrumentationUtility.gotoHome()

        InstrumentationUtility.openOptionsMenu()
        onView(withText(homeMenuItems[4]))
                .perform(click())
        onView(withText("CommCare > Advanced"))
                .check(matches(isDisplayed()))
        checkStringExists(advancedOptions)
        InstrumentationUtility.gotoHome()

        InstrumentationUtility.openOptionsMenu()
        onView(withText(homeMenuItems[5]))
                .perform(click())
        onView(withText("CommCare > Settings"))
                .check(matches(isDisplayed()))
        checkStringExists(arrayOf(
                "Auto Update Frequency",
                "Set Print Template",
                "Grid Menus Enabled",
                "Fuzzy Search Matches",
                "Opt Out of Analytics"))
        InstrumentationUtility.gotoHome()
    }

    @Test
    @BrowserstackTests
    fun testAdvancedActions() {
        openAdvancedOption(0)
        onView(withText("Do you want to send, receive, or submit forms?"))
                .check(matches(isDisplayed()))
        onView(withId(R.id.negative_button))
                .perform(click())
        Intents.intended(IntentMatchers.hasComponent(CommCareWiFiDirectActivity::class.java.name))
        InstrumentationUtility.gotoHome()

        openAdvancedOption(1)
        val formDumpConfirmation = "Do not use this feature unless you have been trained to do so. Do you wish to proceed?"
        onView(withText(formDumpConfirmation))
                .check(matches(isDisplayed()))
        onView(withId(R.id.negative_button))
                .perform(click())
        onView(withText("CommCare > Advanced"))
                .check(matches(isDisplayed()))
        onView(withText(advancedOptions[1]))
                .perform(click())
        onView(withText(formDumpConfirmation))
                .check(matches(isDisplayed()))
        onView(withId(R.id.positive_button))
                .perform(click())
        onView(withText(startsWith("Dump Forms")))
                .check(matches(isDisplayed()))
        onView(withText(startsWith("Submit Forms")))
                .check(matches(isDisplayed()))
        Espresso.pressBack()
        onView(withText(formDumpConfirmation))
                .check(doesNotExist())
        InstrumentationUtility.gotoHome()

        openAdvancedOption(4)
        Intents.intended(IntentMatchers.hasComponent(CommCareVerificationActivity::class.java.name))
        onView(withId(R.id.home_gridview_buttons))
                .check(matches(isDisplayed()))

        openAdvancedOption(6)
        Intents.intended(IntentMatchers.hasComponent(RecoveryActivity::class.java.name))
        InstrumentationUtility.gotoHome()

        openAdvancedOption(7)
        onView(withId(R.id.positive_button))
                .perform(click())
        onView(withText("Welcome back! Please log in."))
                .check(matches(isDisplayed()))
    }

    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
    @Test
    fun runConnectionTest() {
        openAdvancedOption(5)
        onView(withId(R.id.run_connection_test))
                .perform(click())
        onView(withText("No problems were detected."))
                .check(matches(isDisplayed()))
        InstrumentationUtility.changeWifi(false)
        onView(withId(R.id.run_connection_test))
                .perform(click())
        onView(withText("You are not connected the Internet. Please run this test again after connecting to Wi-Fi or mobile data."))
                .check(matches(isDisplayed()))
        InstrumentationUtility.changeWifi(true)
        InstrumentationUtility.logout()
    }

    private fun openAdvancedOption(index: Int) {
        InstrumentationUtility.openOptionsMenu()
        onView(withText(homeMenuItems[4]))
                .perform(click())
        onView(withText(advancedOptions[index]))
                .perform(click())
    }

    private fun checkStringExists(arr: Array<String>) {
        arr.forEach { item ->
            onView(withText(item))
                    .check(matches(isDisplayed()))
        }
    }
}
