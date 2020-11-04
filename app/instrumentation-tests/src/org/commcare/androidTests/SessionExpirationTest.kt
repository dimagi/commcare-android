package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.activities.FormEntryActivity
import org.commcare.activities.LoginActivity
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class SessionExpirationTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "session_expiration.ccz"
        const val APP_NAME = "Session Expiration Test"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
    }

    @Test
    fun testSessionExpiration_redirectsToLogin() {
        // Redirection from home screen
        InstrumentationUtility.login("user_with_no_data", "123")
        InstrumentationUtility.sleep(35)
        InstrumentationUtility.assertCurrentActivity(LoginActivity::class.java)

        // Redirection from menu screen
        InstrumentationUtility.login("user_with_no_data", "123")
        onView(withText("Start"))
                .perform(click())
        InstrumentationUtility.sleep(35)
        InstrumentationUtility.assertCurrentActivity(LoginActivity::class.java)

        // Redirection from FormEntry screen
        InstrumentationUtility.login("user_with_no_data", "123")
        InstrumentationUtility.openForm(0, 0)
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withText("2"))
                .perform(click())
        InstrumentationUtility.sleep(30)
        InstrumentationUtility.assertCurrentActivity(LoginActivity::class.java)

        // Check that session expiration saves form as incomplete and re-opens the form screen directly.
        InstrumentationUtility.login("user_with_no_data", "123")
        InstrumentationUtility.assertCurrentActivity(FormEntryActivity::class.java)
    }

}
