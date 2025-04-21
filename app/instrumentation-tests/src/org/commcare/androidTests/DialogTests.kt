package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.doesNotExist
import org.commcare.utils.isDisplayed
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class DialogTests: BaseTest() {

    companion object {
        const val CCZ_NAME = "integration_test_app.ccz"
        const val APP_NAME = "Integration Tests"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test_user_4", "123")
    }

    @Test
    fun testDialogCreation() {
        InstrumentationUtility.openModule("Errors")
        onView(withText("Error on open"))
                .perform(click())

        checkDialogExistence_withRotation("Error Occurred")

        onView(withText("Error on repeat creation"))
                .perform(click())
        onView(withId(R.id.nav_btn_next))
                .perform(click())

        onView(withId(R.id.choice_dialog_panel_2)).check(matches(withText("Add a new Error on add?")))

        InstrumentationUtility.rotateLeft()
        //TODO Expect dialog to not persist due to a activity lifecycle bug in our dialog framework.
        withText(R.id.choice_dialog_panel_2).doesNotExist()

        InstrumentationUtility.rotatePortrait()
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withId(R.id.choice_dialog_panel_2))
                .perform(click())

        checkDialogExistence_withRotation("Error Occurred")

        InstrumentationUtility.gotoHome()
        InstrumentationUtility.selectOptionItem(withText("About CommCare"))
        checkDialogExistence_withRotation("OK")
    }

    private fun checkDialogExistence_withRotation(text: String) {
        withText(text).isDisplayed()
        InstrumentationUtility.rotateLeft()
        withText(text).isDisplayed()
        InstrumentationUtility.rotatePortrait()
        onView(withText("OK"))
                .perform(click())
    }

}
