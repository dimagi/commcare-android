package org.commcare.androidTests

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.CommCareApplication
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
import org.hamcrest.Matchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class MobilePinTests: BaseTest() {
    companion object {
        const val CCZ_NAME_1 = "basic_tests_latest.ccz"
        const val CCZ_NAME_2 = "case_managements_tests.ccz"
        const val APP_NAME_1 = "Basic Tests"
        const val APP_NAME_2 = "Case Managements!"
    }

    @Before
    fun setup() {
        if (CommCareApplication.instance().currentApp == null) {
            InstrumentationUtility.installApp(CCZ_NAME_1)
        } else {
            InstrumentationUtility.uninstallCurrentApp()
            InstrumentationUtility.installApp(CCZ_NAME_1)
            Espresso.pressBack()
        }
        InstrumentationUtility.login("test1", "123")
    }



    @Test
    fun testMobilePINSetup(){
        testMobilePINForSingleApp()
        testMobilePINForMultipleApps()
    }

    fun testMobilePINForSingleApp(){
        enablePIN()

        InstrumentationUtility.login("test1", "123")
        assertTrue(onView(withText("Create a PIN?")).isPresent())
        verifyCreatePINWindow()
        onView(withText("NO, BUT ASK AGAIN ON NEXT LOGIN")).perform(click())
        assertFalse(onView(withText("Create a PIN?")).isPresent())
        InstrumentationUtility.logout()

        InstrumentationUtility.login("test1", "123")
        assertTrue(onView(withText("Create a PIN?")).isPresent())
        setPIN("1234")
        InstrumentationUtility.logout()

        assertTrue(onView(withHint("PIN")).isPresent())
        InstrumentationUtility.login("test1", "1234")
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Change My PIN"))
            .perform(click())
        assertTrue(onView(withText("Enter your current PIN")).isPresent())
        onView(withId(R.id.pin_entry))
            .perform(ViewActions.typeText(("1234")))
        onView(withText("ENTER")).perform(click())
        setNewPIN("4321")
        InstrumentationUtility.logout()

        assertTrue(onView(withHint("PIN")).isPresent())
        InstrumentationUtility.login("test1", "4321")
        InstrumentationUtility.logout()

        assertTrue(onView(withHint("PIN")).isPresent())
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Forgot PIN?"))
            .perform(click())
        assertTrue(onView(withHint("Password")).isPresent())
        onView(withHint("Password")).perform(typeText("123"))
        onView(withId(R.id.login_button))
            .perform(click())
        assertTrue(onView(withText("Reset your PIN?")).isPresent())
        verifyCreatePINWindow()
        onView(withText("YES, SET MY PIN NOW")).perform(click())
        setNewPIN("6789")
        InstrumentationUtility.logout()
        assertTrue(onView(withHint("PIN")).isPresent())
        InstrumentationUtility.login("test1", "6789")
        InstrumentationUtility.logout()

        assertTrue(onView(withHint("PIN")).isPresent())
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Forgot PIN?"))
            .perform(click())
        assertTrue(onView(withHint("Password")).isPresent())
        onView(withHint("Password")).perform(typeText("123"))
        onView(withId(R.id.login_button))
            .perform(click())
        assertTrue(onView(withText("Reset your PIN?")).isPresent())
        verifyCreatePINWindow()

        onView(withText("NO, BUT ASK AGAIN ON NEXT LOGIN")).perform(click())
        InstrumentationUtility.logout()
        assertTrue(onView(withHint("PIN")).isPresent())
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Forgot PIN?"))
            .perform(click())
        assertTrue(onView(withHint("Password")).isPresent())
        onView(withHint("Password")).perform(typeText("123"))
        onView(withId(R.id.login_button))
            .perform(click())
        assertTrue(onView(withText("Reset your PIN?")).isPresent())
        verifyCreatePINWindow()
        onView(withText("NO, BUT ASK AGAIN ON NEXT LOGIN")).perform(click())
        InstrumentationUtility.logout()

    }


    fun testMobilePINForMultipleApps() {
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Go To App Manager"))
            .perform(click())
        onView(withId(R.id.install_app_button))
            .perform(click())
        InstrumentationUtility.installApp(CCZ_NAME_2)
        pressBack()
        onView(withId(R.id.app_selection_spinner)).perform(click())
        onView(withText(APP_NAME_2)).perform(click())
        InstrumentationUtility.login("test1", "123")
        enablePIN()
        InstrumentationUtility.login("test1", "123")
        onView(withText("I'LL UPDATE LATER")).perform(click())
        InstrumentationUtility.logout()
        InstrumentationUtility.login("test1", "123")
        assertTrue(onView(withText("Create a PIN?")).isPresent())
        setPIN("1234")
        InstrumentationUtility.logout()

        uninstallApp(APP_NAME_1)
        pressBack()
        InstrumentationUtility.login("test1", "1234")
        onView(withText("I'LL UPDATE LATER")).perform(click())
        InstrumentationUtility.logout()
        InstrumentationUtility.login("test1", "1234")
        assertTrue(onView(withText("Start")).isPresent())


    }

    private fun verifyCreatePINWindow(){
        assertTrue(onView(withText("YES, SET MY PIN NOW")).isPresent())
        assertTrue(onView(withText("NO, BUT ASK AGAIN ON NEXT LOGIN")).isPresent())
        assertTrue(onView(withText("NO, AND DON'T ASK AGAIN")).isPresent())
        assertTrue(onView(withId(R.id.extra_info_button)).isPresent())
    }


    private fun enablePIN(){
        InstrumentationUtility.enableDeveloperMode()
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
            .perform(click())
        onView(withText("Developer Options"))
            .perform(click())
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Mobile Privileges"))
            .perform(click())
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
            .perform(click())
        onView(withText("Developer Options"))
            .perform(click())
        onView(withText("Give option to use PIN for login"))
            .perform(click())
        onView(withText("Enabled"))
            .perform(click())
        InstrumentationUtility.logout()

    }
    private fun setPIN(pin: String){
        onView(withText("YES, SET MY PIN NOW")).perform(click())
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

    private fun setNewPIN(newpin: String){
        assertTrue(onView(withText("Enter a new 4-digit PIN")).isPresent())
        onView(withId(R.id.pin_entry))
            .perform(ViewActions.typeText((newpin)))
        onView(withText("CONTINUE")).perform(click())
        assertTrue(onView(withText("Re-Enter the PIN you chose")).isPresent())
        onView(withId(R.id.pin_entry))
            .perform(ViewActions.typeText((newpin)))
        onView(withText("CONFIRM")).perform(click())
        InstrumentationUtility.waitForView(withText("PIN set successfully"))
    }
    fun uninstallApp(cczName: String) {
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Go To App Manager"))
            .perform(click())
        if (onView(withText(cczName)).isPresent()) {
            onView(withText(cczName)).perform(click())
//        InstrumentationUtility.clickListItem(R.id.apps_list_view, 0)
            onView(withText("Uninstall"))
                .perform(click())
            onView(withText("OK"))
                .inRoot(RootMatchers.isDialog())
                .perform(click())
        }
        else{
            Espresso.pressBack()
        }
    }

}
