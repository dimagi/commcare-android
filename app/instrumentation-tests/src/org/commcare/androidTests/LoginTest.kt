package org.commcare.androidTests

import android.os.Build
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.hamcrest.Matchers.startsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LoginTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "test_list_search.ccz"
        const val APP_NAME = "Test: List Searching"
        val homeButtons = arrayOf("Start", "Sync with Server", "Log out of CommCare")
        val demoHomeButtons = arrayOf(
                "Explore CommCare Practice Mode",
                "Submit Practice Data to Server",
                "Log out of Practice Mode"
        )
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
    }

    @Test
    @BrowserstackTests
    fun testLoginFlow() {
        InstrumentationUtility.login("user_with_no_data", "123")
        verifyAllHomeButtonsPresent(homeButtons)
        InstrumentationUtility.logout()

        // Login in landscape mode
        InstrumentationUtility.rotateLeft()
        Espresso.closeSoftKeyboard()
        InstrumentationUtility.login("user_with_no_data", "123")
        verifyAllHomeButtonsPresent(homeButtons)
        InstrumentationUtility.logout()

        InstrumentationUtility.rotatePortrait()

        // Enter practice Mode.
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Enter Practice Mode"))
                .perform(click())
        onView(withText("Starting Practice Mode"))
                .check(matches(isDisplayed()))
        InstrumentationUtility.rotateLeft()
        onView(withText("Starting Practice Mode"))
                .check(matches(isDisplayed()))
        InstrumentationUtility.rotatePortrait()
        onView(withId(R.id.positive_button))
                .perform(click())
        onView(withText(startsWith("You are logging into")))
                .check(doesNotExist())

        verifyAllHomeButtonsPresent(demoHomeButtons)

        onView(withId(R.id.home_gridview_buttons))
                .perform(swipeUp())
        onView(withText(demoHomeButtons[2]))
                .perform(click())

        // Login with wrong password.
        InstrumentationUtility.login("user_with_no_data", "badpass")
        onView(withText("Invalid Username or Password"))
                .check(matches(isDisplayed()))

        // Login with wrong username
        InstrumentationUtility.login("fake user", "badpass")
        onView(withText("Invalid Username or Password"))
                .check(matches(isDisplayed()))
    }

    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
    @Test
    fun testLoginFlow_withoutInternet() {
        // Need one login while being connected so that the subsequent one without internet works.
        InstrumentationUtility.login("user_with_no_data", "123")
        InstrumentationUtility.logout()

        // Login Offline
        InstrumentationUtility.changeWifi(false)
        InstrumentationUtility.login("user_with_no_data", "123")
        verifyAllHomeButtonsPresent(homeButtons)
        InstrumentationUtility.logout()

        // login offline with bad password
        InstrumentationUtility.login("user_with_no_data", "badpass")
        onView(withText("Either the password you entered was incorrect, or CommCare couldn't reach the server"))
                .check(matches(isDisplayed()))

        // login with bad username
        InstrumentationUtility.login("fake user", "badpass")
        onView(withText("Couldn't Reach Server. Please check your network connection"))
                .check(matches(isDisplayed()))

        InstrumentationUtility.changeWifi(true)
        onView(withId(R.id.edit_password))
                .perform(clearText())
        onView(withId(R.id.login_button))
                .perform(click())
        onView(withText("Empty Password"))
                .check(matches(isDisplayed()))
    }

    private fun verifyAllHomeButtonsPresent(arr: Array<String>) {
        arr.forEach { item ->
            onView(withId(R.id.home_gridview_buttons)).perform(repeatedlyUntil(swipeUp(),
                    hasDescendant(withText(item)),
                    5))
        }
    }
}
