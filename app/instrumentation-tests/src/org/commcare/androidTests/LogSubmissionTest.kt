package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isDisplayed
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LogSubmissionTest: BaseTest() {
    companion object {
        const val CCZ_NAME = "integration_test_app.ccz"
        const val APP_NAME = "Integration Tests"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test", "123")
    }

    @Test
    fun testLogSubmission() {
        InstrumentationUtility.openModule("Errors")
        onView(withText("Error on open"))
                .perform(click())
        withText("Error Occurred").isDisplayed()
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.selectOptionItem(withText("Advanced"))
        onView(withText("Force Log Submission"))
                .perform(click())
        InstrumentationUtility.assert(
                InstrumentationUtility.loadLogSubmissionInfo(),
                "Log submission was unsuccessful"
        )
    }
}
