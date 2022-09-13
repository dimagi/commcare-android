package org.commcare.androidTests


import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.PositionAssertions.isAbove
import androidx.test.espresso.assertion.PositionAssertions.isBelow
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.commcare.CommCareApplication
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isDisplayed
import org.commcare.utils.isPresent
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class DeveloperOptionsTests : BaseTest() {

    companion object {
        const val CCZ_NAME = "basic_tests.ccz"
        const val APP_NAME = "Basic Tests"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test1", "123")
    }

    @After
    fun tearDown() {
        InstrumentationUtility.logout()
    }

    @Test
    fun testDevelopersOptions() {
        InstrumentationUtility.enableDeveloperMode()
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
            .perform(click())
        onView(withText("Developer Options"))
            .perform(click())
        onView(withText("Developer Mode Enabled")).isPresent()
        onView(withText("Show Update Options Item")).isPresent()
        onView(withText("Image Above Question Text Enabled")).isPresent()
        onView(withText("Detect and Trigger Purge on Form Save")).isPresent()
        InstrumentationUtility.gotoHome()
        testUpdateOptions()
        testImageAboveText()
        testImageBelowText()
    }

    fun testUpdateOptions(){
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
            .perform(click())
        onView(withText("Developer Options"))
            .perform(click())
        onView(withText("Show Update Options Item"))
            .perform(click())
        onView(withText("Enabled"))
            .perform(click())
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
            .perform(click())
        onView(withText("Update Options"))
            .perform(click())
        onView(withText("Latest saved state"))
            .perform(click())
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Update App"))
            .perform(click())
        InstrumentationUtility.waitForView(withText("App is up to date"))
        onView(withText("App is up to date")).isPresent()
        InstrumentationUtility.gotoHome()
    }
    fun testImageAboveText(){
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
            .perform(click())
        onView(withText("Developer Options"))
            .perform(click())
        onView(withText("Image Above Question Text Enabled"))
            .perform(click())
        onView(withText("Enabled"))
            .perform(click())
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openForm(0,0)
        onView(withId(R.id.image)).check(isAbove(withSubstring("Enter a Name")))
        onView(withClassName(endsWith("EditText"))).perform(typeText("Image above text"))
        InstrumentationUtility.submitForm()
        InstrumentationUtility.gotoHome()
    }
    fun testImageBelowText(){
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
            .perform(click())
        onView(withText("Developer Options"))
            .perform(click())
        onView(withText("Image Above Question Text Enabled"))
            .perform(click())
        onView(withText("Disabled"))
            .perform(click())
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openForm(0,0)
        onView(withId(R.id.image)).check(isBelow(withSubstring("Enter a Name")))
        onView(withClassName(endsWith("EditText"))).perform(typeText("Image below text"))
        InstrumentationUtility.submitForm()
        InstrumentationUtility.gotoHome()
    }

}
