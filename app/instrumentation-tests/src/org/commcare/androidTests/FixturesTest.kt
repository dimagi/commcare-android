package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class FixturesTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "fixtures.ccz"
        const val APP_NAME = "Fixtures"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
    }

    @Test
    fun testMissingFixture_throwsError() {
        InstrumentationUtility.login("fixtures_fails", "123")
        openFixtureForm()
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withText("Error Occurred"))
                .check(matches(isDisplayed()))
        onView(withText(startsWith("Make sure the 'Test' lookup table is available")))
                .check(matches(isDisplayed()))
    }

    @Test
    fun testFixturesWork() {
        InstrumentationUtility.login("fixtures_works", "123")
        openFixtureForm_andSelectFirstTwoCheckboxes()
        onView(withText("Essex"))
                .perform(click())
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withText("Saugus"))
                .perform(click())
        onView(withId(R.id.nav_btn_finish))
                .perform(click())
        onView(withText("Saved"))
                .perform(click())
        onView(withText("Fixtures Form"))
                .perform(click())
        onView(withText("3 3 "))
                .check(matches(isDisplayed()))
        onView(withText("Essex"))
                .check(matches(isDisplayed()))
        onView(withText("Saugus"))
                .check(matches(isDisplayed()))
    }

    @Test
    fun testFixtureFiltering() {
        InstrumentationUtility.login("fixtures_works", "123")
        openFixtureForm_andSelectFirstTwoCheckboxes()
        // Proceed without selecting country
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        // confirm you don't see any radio button now to select a city.
        onView(withClassName(endsWith("RadioButton")))
                .check(doesNotExist())

        // Go back now and pick a country and confirm the cities are filtered.
        onView(withId(R.id.nav_btn_prev))
                .perform(click())
        onView(withText("Essex"))
                .perform(click())
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withText("Saugus"))
                .check(matches(isDisplayed()))
        onView(withText("Andover"))
                .check(matches(isDisplayed()))

        onView(withId(R.id.nav_btn_prev))
                .perform(click())
        onView(withText("Middlesex"))
                .perform(click())
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withText("Cambridge"))
                .check(matches(isDisplayed()))
        onView(withText("Wilmington"))
                .check(matches(isDisplayed()))
        onView(withText("Billerica"))
                .check(matches(isDisplayed()))

        onView(withId(R.id.nav_btn_prev))
                .perform(click())
        onView(withText("Suffolk"))
                .perform(click())
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withText("Boston"))
                .check(matches(isDisplayed()))
        onView(withText("Winthrop"))
                .check(matches(isDisplayed()))

        onView(withText("Boston"))
                .perform(click())
        onView(withId(R.id.nav_btn_finish))
                .perform(click())
    }

    @Test
    fun testFixture_with1Mb_works() {
        InstrumentationUtility.login("fixtures_1MB", "123")
        onView(withText("Start"))
                .perform(click())
        InstrumentationUtility.clickListItem(R.id.screen_suite_menu_list, 0)
        onView(withText("1MB Fixture"))
                .perform(click())
        onView(withText(startsWith("This form contains a 1MB fixture.")))
                .check(matches(isDisplayed()))
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withText("Please select an option."))
                .check(matches(isDisplayed()))
        onView(withText(startsWith("Increase Enalapril")))
                .perform(click())
        onView(withId(R.id.nav_btn_finish))
                .perform(click())
    }

    private fun openFixtureForm() {
        onView(withText("Start"))
                .perform(click())
        InstrumentationUtility.clickListItem(R.id.screen_suite_menu_list, 0)
        onView(withText("Fixtures Form"))
                .perform(click())
    }

    private fun openFixtureForm_andSelectFirstTwoCheckboxes() {
        openFixtureForm()
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        // Press first 2 checkboxes
        onView(CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                1
        )).perform(click())
        onView(CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                2
        )).perform(click())
        onView(withId(R.id.nav_btn_next))
                .perform(click())
    }

}