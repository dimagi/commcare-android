package org.commcare.androidTests


import android.app.Service
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.ContextCompat.startActivity
import androidx.test.InstrumentationRegistry.getInstrumentation
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.commcare.CommCareInstrumentationTestApplication
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith



@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class RootMenuAsHomeScreenTest : BaseTest() {

    companion object {
        const val CCZ_NAME = "root_menu_as_home_screen.ccz"
        const val APP_NAME = "Root menu as Home screen"

    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test1", "123")
    }


    @Test
    fun testRootMenuAsHomeScreen() {
        assertTrue(onView(withId(R.id.grid_menu_grid)).isPresent())
        verifyHomeScreenNavMenu()

        InstrumentationUtility.clickListItem(R.id.grid_menu_grid, 0)
        InstrumentationUtility.clickListItem(R.id.screen_suite_menu_list, 0)
        onView(withClassName(endsWith("EditText"))).perform(typeText("test for complete form"))
        InstrumentationUtility.submitForm()
        verifyHomeScreenNavMenu()

        InstrumentationUtility.clickListItem(R.id.grid_menu_grid, 0)
        InstrumentationUtility.clickListItem(R.id.screen_suite_menu_list, 0)
        InstrumentationUtility.submitForm()
        verifyHomeScreenNavMenu()

        InstrumentationUtility.clickListItem(R.id.grid_menu_grid, 0)
        InstrumentationUtility.clickListItem(R.id.screen_suite_menu_list, 0)
        onView(withClassName(endsWith("EditText"))).perform(typeText("test for complete form"))
        InstrumentationUtility.exitForm(R.string.do_not_save)
        Espresso.pressBack()
        verifyHomeScreenNavMenu()

        val application = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .applicationContext as CommCareInstrumentationTestApplication
        val activity = application.currentActivity
        activity.moveTaskToBack(true)

        Thread.sleep(2000)
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.pressHome()
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_APP_SWITCH)
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_APP_SWITCH)
        verifyHomeScreenNavMenu()
    }
    private fun verifyHomeScreenNavMenu(){
        assertTrue(onView(withContentDescription("Navigate up")).isPresent())
        onView(withContentDescription("Navigate up")).perform(click())
//        onView(withTagValue(CoreMatchers.`is`(R.drawable.ic_hamburger_menu))).check(matches(isDisplayed()))
//        onView(withTagValue(CoreMatchers.`is`(R.drawable.ic_hamburger_menu))).perform(click())
        assertTrue(onView(withId(R.id.nav_drawer)).isPresent())
        assertTrue(onView(withText("About CommCare")).isPresent())
        assertTrue(onView(withText("Settings")).isPresent())
        assertTrue(onView(withText("Advanced")).isPresent())
        assertTrue(onView(withText("Update App")).isPresent())
        assertTrue(onView(withText("Sync with Server")).isPresent())
        assertTrue(onView(withText("Log out of CommCare")).isPresent())
//        onView(withTagValue(CoreMatchers.`is`(R.drawable.ic_hamburger_menu))).perform(click())
        onView(withContentDescription("Navigate up")).perform(click())
    }

}
