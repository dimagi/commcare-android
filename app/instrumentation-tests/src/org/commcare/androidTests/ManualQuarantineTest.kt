package org.commcare.androidTests

import android.os.Build
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import org.commcare.annotations.WifiDisabled
import org.commcare.dalvik.R
import org.commcare.utils.*
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@LargeTest
@WifiDisabled
@SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ManualQuarantineTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "ccqa.ccz"
        const val APP_NAME = "Basic Tests"
        const val MODULE_DISPLAY_FORM = 12
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test", "1234")
        // Enable quarantine
        enableFormQuarantine()
        InstrumentationUtility.changeWifi(false)
    }

    @After
    fun tearDown() {
        InstrumentationUtility.logout()
    }

    private fun enableFormQuarantine() {
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.selectOptionItem(withText("Advanced"))
        onView(withText("Enable Manual Form Quarantine"))
                .perform(click())
        InstrumentationUtility.gotoHome()
    }

    @Test
    fun test_A_Quarantine() {
        InstrumentationUtility.openModule(MODULE_DISPLAY_FORM)
        onView(withId(R.id.nav_btn_finish))
                .perform(click())
        InstrumentationUtility.openModule(MODULE_DISPLAY_FORM)
        onView(withId(R.id.nav_btn_finish))
                .perform(click())

        // Go to saved forms and quarantine them
        InstrumentationUtility.selectOptionItem(withText("Saved Forms"))

        // Quarantine first form.
        onView(CustomMatchers.find(
                allOf(withText("Display Form")),
                1
        )).perform(longClick())
        // Unsent forms should not be delete-able
        withText("Delete Record").doesNotExist()
        onView(withText("Scan Record Integrity"))
                .perform(click())
        onView(withText("QUARANTINE FORM"))
                .perform(click())

        // After quarantining one form we can't quarantine another before re-enabling it from setting.
        onView(withText("Display Form"))
                .perform(longClick())
        onView(withText("Scan Record Integrity"))
                .perform(click())
        withText("QUARANTINE FORM").doesNotExist()
        onView(withText("OK"))
                .perform(click())

        enableFormQuarantine()

        // Quarantine second form
        InstrumentationUtility.selectOptionItem(withText("Saved Forms"))
        onView(withText("Display Form"))
                .perform(longClick())
        withText("Delete Record").doesNotExist()
        onView(withText("Scan Record Integrity"))
                .perform(click())
        onView(withText("QUARANTINE FORM"))
                .perform(click())
    }

    @Test
    fun test_B_FormSubmission_withQuarantineForm() {
        InstrumentationUtility.selectOptionItem(withText("Saved Forms"))
        onView(withId(R.id.entity_select_filter_dropdown))
                .perform(click())
        onView(withText("Filter: Quarantined Forms"))
                .perform(click())

        // Send 1 form back to unsent queue
        onView(CustomMatchers.find(
                allOf(withText("Display Form")),
                1
        )).perform(longClick())
        onView(withText("Add Record Back to Unsent Queue"))
                .perform(click())

        // Confirm there is 1 form left in quarantine, and that you can delete it
        onView(withText("Display Form"))
                .perform(longClick())
        onView(withText("Delete Record"))
                .perform(click())
        withText("Display Form").doesNotExist()

        // Confirm 1 form is now in unsent
        onView(withId(R.id.entity_select_filter_dropdown))
                .perform(click())
        onView(withText("Filter By: Only Unsent Forms"))
                .perform(click())
        withText("Display Form").isDisplayed()

        InstrumentationUtility.changeWifi(true)
        InstrumentationUtility.gotoHome()
        onView(withText("Sync with Server"))
                .perform(click())
        withText("Sync Successful! Your information is up to date.").isDisplayed()
    }
}
