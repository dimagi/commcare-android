package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers.matchListSize
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isDisplayed
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class DemoUserOnlineTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "demo_user_test_1.ccz"
        const val APP_NAME = "Demo User Restore Test"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
    }

    @Test
    fun testPracticeMode() {
        // Enter practice mode
        enterPracticeMode()

        onView(withId(R.id.home_gridview_buttons))
                .perform(swipeUp())
        withText("Logged In: demo.user.test").isDisplayed()

        // Confirm that the right restore has been used
        onView(withId(R.id.home_gridview_buttons))
                .perform(swipeDown())
        openForm("Case List", "Followup")

        onView(withId(R.id.screen_entity_select_list))
                .check(matches(matchListSize(3)))
        matchListItems(arrayOf("alligator", "animal", "apple"))

        // Create another case and check that it shows up in the case list
        InstrumentationUtility.gotoHome()
        openForm("Case List", "Register")

        onView(withClassName(endsWith("EditText")))
                .perform(typeText("carrot"))
        onView(withId(R.id.nav_btn_finish))
                .perform(click())

        openForm("Case List", "Followup")
        onView(withId(R.id.screen_entity_select_list))
                .check(matches(matchListSize(4)))
        matchListItems(arrayOf("alligator", "animal", "apple", "carrot"))
    }

    @Test
    fun testAppUpdate() {
        // Perform an update to an app version with a different demo user
        InstrumentationUtility.login("test", "123")
        InstrumentationUtility.selectOptionItem(withText("Update App"))
        InstrumentationUtility.selectOptionItem(withText("Offline Update"))

        InstrumentationUtility.stubCcz("demo_user_test_2.ccz")
        onView(withId(R.id.screen_multimedia_inflater_filefetch))
                .perform(click())
        onView(withText("Update App"))
                .perform(click())
        onView(withText(startsWith("Update to version")))
                .perform(click())

        // Enter practice mode
        enterPracticeMode()

        onView(withId(R.id.home_gridview_buttons))
                .perform(swipeUp())
        withText("Logged In: demo.user.test.2").isDisplayed()

        // Confirm that the right restore has been used
        onView(withId(R.id.home_gridview_buttons))
                .perform(swipeDown())
        openForm("Case List", "Followup")
        matchListItems(arrayOf("balloon", "block", "bear"))
    }

    private fun enterPracticeMode() {
        InstrumentationUtility.selectOptionItem(withText("Enter Practice Mode"))
        withText("Starting Practice Mode").isDisplayed()
        onView(withId(R.id.positive_button))
                .perform(click())
    }

    private fun openForm(module: String, form: String) {
        onView(withText("Explore CommCare Practice Mode"))
                .perform(click())
        onView(withText(module))
                .perform(click())
        onView(withText(form))
                .perform(click())
    }

    private fun matchListItems(items: Array<String>) {
        items.forEach {
            onView(allOf(
                    withId(R.id.entity_view_text),
                    withText(it)
            )).check(matches(isDisplayed()))
        }
    }
}
