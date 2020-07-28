package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SubmissionErrorResponseTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "invalid_index_app.ccz"
        const val APP_NAME = "App with Child Cases"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test", "123")
    }

    @Test
    fun submitForm_withWrongCaseIndex_shouldQuarantine() {
        onView(withText("Start"))
                .perform(click())
        onView(withText("Parents"))
                .perform(click())
        onView(withText("Create Child with invalid index"))
                .perform(click())
        onView(withText("Parent 2"))
                .perform(click())
        onView(withText("Continue"))
                .perform(click())

        onView(withClassName(endsWith("EditText")))
                .perform(typeText("Case that will cause processing error"))
        onView(withId(R.id.nav_btn_finish))
                .perform(click())

        // Form should get quarantined.
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Saved Forms"))
                .perform(click())
        onView(withText(startsWith("Filter By")))
                .perform(click())
        onView(withText(endsWith("Quarantined Forms")))
                .perform(click())

        onView(withId(R.id.screen_entity_select_list))
                .check(matches(InstrumentationUtility.matchListSize(1)))
    }
}