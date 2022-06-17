package org.commcare.androidTests

import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import junit.framework.TestCase.assertTrue
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isDisplayed
import org.commcare.utils.isPresent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class LogSubmissionTest: BaseTest() {
    companion object {
        const val CCZ_NAME = "integration_test_app.ccz"
        const val APP_NAME = "Integration Tests"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test_user_13", "123")
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
                InstrumentationUtility.didLastLogSubmissionSucceed(),
                "Log submission was unsuccessful"
        )
    }

    @Test
    fun testReportProblem() {
        InstrumentationUtility.selectOptionItem(withText("Advanced"))
        onView(withText("Report Problem"))
            .perform(click())
        InstrumentationUtility.enterText(R.id.ReportText01,"This is a test for Report Problem")
        InstrumentationUtility.hideKeyboard()
        onView(withText("Submit Report")).isPresent()
        InstrumentationUtility.rotateLeft()
        InstrumentationUtility.matchTypedText(R.id.ReportText01,"This is a test for Report Problem")
        InstrumentationUtility.hideKeyboard()
        onView(withText("Submit Report")).isPresent()
        InstrumentationUtility.rotatePortrait()
        InstrumentationUtility.hideKeyboard()
        onView(withText("Submit Report")).perform(click())
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_SEND)
        InstrumentationUtility.verifyIntentText("This is a test for Report Problem")
        InstrumentationUtility.hardPressBack()
        assertTrue("Returned to Home page",onView(ViewMatchers.withId(R.id.home_gridview_buttons)).isPresent())
    }
}
