package org.commcare.androidTests


import android.content.Intent
import android.util.Log
import android.widget.TextView
import androidx.test.espresso.Espresso
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
import androidx.test.runner.intent.IntentCallback
import androidx.test.runner.intent.IntentMonitorRegistry
import androidx.test.uiautomator.UiDevice
import junit.framework.TestCase
import org.commcare.CommCareApplication
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isDisplayed
import org.commcare.utils.isPresent
import org.hamcrest.Matchers
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class AdvancedSettingsTests : BaseTest() {

    companion object {
        const val CCZ_NAME = "advanced_settings.ccz"
        const val APP_NAME = "Advanced Settings"
        val NAME = "test"+(LocalDateTime.now()).format(DateTimeFormatter.BASIC_ISO_DATE)+List(1){ Random.nextInt(1000, 9999) }
    }

    @Before
    fun setup() {

        if (CommCareApplication.instance().currentApp == null) {
            InstrumentationUtility.installApp(CCZ_NAME)
        } else {
            InstrumentationUtility.uninstallCurrentApp()
            InstrumentationUtility.installApp(CCZ_NAME)
            Espresso.pressBack()
        }
        InstrumentationUtility.login("test1", "123")
    }

    @After
    fun tearDown() {
        InstrumentationUtility.logout()
    }

    @Test
    fun testAdvancedSettings() {
        assertTrue(onView(withText("Create a PIN?")).isPresent())
        verifyCreatePINWindow()
        onView(withText("NO, AND DON'T ASK AGAIN")).perform(click())
        InstrumentationUtility.waitForView(withText("Setting A PIN Later"))
        onView(withText("OK")).perform(click())
        InstrumentationUtility.logout()
        InstrumentationUtility.waitForView(withText("LOG IN"))

        InstrumentationUtility.login("test1", "123")
        assertFalse(onView(withText("Create a PIN?")).isPresent())

        InstrumentationUtility.openOptionsMenu()
        onView(withText("Set My PIN")).perform(click())

        assertTrue(onView(withText("Enter your password")).isPresent())
        onView(withId(R.id.password_entry)).perform(typeText("123"))
        onView(withText("ENTER")).perform(click())
        setPin("1234")

        InstrumentationUtility.logout()
        assertTrue(onView(withHint("PIN")).isPresent())
        InstrumentationUtility.login("test1", "4321")
        assertTrue(onView(withText("Invalid PIN")).isPresent())

        InstrumentationUtility.login("test1", "1234")
        assertTrue(onView(withText("Report an Issue")).isPresent())
        onView(withText("Report an Issue")).perform(click())

        reportIssue("This is a test to Report an Issue")
        InstrumentationUtility.logout()
        InstrumentationUtility.login("test1","1234")
        enableAndVerifyDevOptions()

        InstrumentationUtility.logout()
        InstrumentationUtility.login("test1","1234")
        InstrumentationUtility.selectOptionItem(withText("Settings"))
        assertTrue(onView(withText("Update Options")).isPresent())
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openOptionsMenu()
        assertTrue(onView(withText("Update App")).isPresent())
        onView(withText("Update App")).perform(click())
        InstrumentationUtility.openOptionsMenu()
        assertTrue(onView(withText("Update Options")).isPresent())
        InstrumentationUtility.gotoHome()

        InstrumentationUtility.selectOptionItem(withText("Advanced"))
        assertFalse(onView(withText("Report Problem")).isPresent())
        InstrumentationUtility.gotoHome()

        InstrumentationUtility.openGridForm(1,0)
        assertTrue(onView(withSubstring("Select answer option cannot contain spaces")).isPresent())
        onView(withText("OK")).perform(click())
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openGridForm(0,0)

        onView(withId(R.id.image)).check(isAbove(withSubstring("Name")))
        onView(withClassName(endsWith("EditText"))).perform(typeText(NAME))
        InstrumentationUtility.nextPage()
        onView(withText("Andover")).perform(click())
        InstrumentationUtility.submitForm()

        assertTrue(onView(withText("1 form sent to server!")).isPresent())

        InstrumentationUtility.openGridForm(0,1)
        onView(withId(R.id.search_action_bar)).perform(click())
        onView(withId(R.id.search_src_text)).perform(typeText(NAME))
        InstrumentationUtility.clickListItem(R.id.screen_entity_select_list, 0)
        onView(isRoot()).perform(ViewActions.swipeLeft())
        assertFalse(onView(withText("FINISH")).isPresent())
        onView(withText("CONTINUE")).perform(click())
        assertTrue(onView(withText("FINISH")).isPresent())
        onView(withText("Boston")).perform(click())
        InstrumentationUtility.submitForm()

        InstrumentationUtility.openGridForm(0,1)
        onView(withId(R.id.search_action_bar)).perform(click())
        onView(withId(R.id.search_src_text)).perform(typeText(NAME))
        val formatter = DateTimeFormatter.ofPattern("yy/MM/d")
        var timeNow = (LocalDateTime.now()).format(formatter)
        val text1 = InstrumentationUtility.getText(onView(withSubstring(timeNow)))
        Log.i("time1:", text1)
        Thread.sleep(15000)
        timeNow = (LocalDateTime.now()).format(formatter)
        val text2 = InstrumentationUtility.getText(onView(withSubstring(timeNow)))
        Log.i("time2:", text2)
        assertNotEquals(text1, text2)
        InstrumentationUtility.gotoHome()

    }

    private fun setPin(pin: String){
        assertTrue(onView(withText("Enter a 4-digit PIN")).isPresent())
        onView(withClassName(Matchers.endsWith("EditText")))
            .perform(ViewActions.typeText((pin)))
        onView(withText("CONTINUE")).perform(click())
        assertTrue(onView(withText("Re-Enter the PIN you chose")).isPresent())
        onView(withClassName(Matchers.endsWith("EditText")))
            .perform(ViewActions.typeText((pin)))
        onView(withText("CONFIRM")).perform(click())
        InstrumentationUtility.waitForView(withText("PIN set successfully"))
    }
    private fun verifyCreatePINWindow(){
        assertTrue(onView(withText("YES, SET MY PIN NOW")).isPresent())
        assertTrue(onView(withText("NO, BUT ASK AGAIN ON NEXT LOGIN")).isPresent())
        assertTrue(onView(withText("NO, AND DON'T ASK AGAIN")).isPresent())
        assertTrue(onView(withId(R.id.extra_info_button)).isPresent())
    }

    private fun reportIssue(reportText: String){
        InstrumentationUtility.enterText(R.id.ReportText01,reportText)
        onView(ViewMatchers.isRoot()).perform(ViewActions.closeSoftKeyboard())
        onView(withText("SUBMIT REPORT")).isPresent()

        var intentcallback = IntentCallback { intent ->
            var extraText = intent.extras!!.getString(Intent.EXTRA_TEXT)
            TestCase.assertTrue(extraText!!.contains(reportText))
        }
        IntentMonitorRegistry.getInstance().addIntentCallback(intentcallback)
        onView(withText("SUBMIT REPORT")).perform(click())

        IntentMonitorRegistry.getInstance().removeIntentCallback(intentcallback)

        InstrumentationUtility.hardPressBack()
        assertTrue(onView(withId(R.id.home_gridview_buttons)).isPresent())
    }

    private fun enableAndVerifyDevOptions(){
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
    }
}
