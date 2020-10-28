package org.commcare.androidTests

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.areDisplayed
import org.commcare.utils.doesNotExist
import org.commcare.utils.isDisplayed
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class FormFilteringTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "test_select_filters.ccz"
        const val APP_NAME = "Test: Select Filters"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
    }

    @Test
    fun testFilter_usingCaseData() {
        val forms = arrayOf("Placeholder", "Case Data Filterable Form")
        InstrumentationUtility.login("test_filter", "123")
        matchCases()

        onView(withText("Select A"))
                .perform(click())

        forms.areDisplayed()

        Espresso.pressBack()
        onView(withText("Select B"))
                .perform(click())

        forms.areDisplayed()

        Espresso.pressBack()
        onView(withText("Select C"))
                .perform(click())

        withText("Placeholder").isDisplayed()
        withText("Case Data Filterable Form").doesNotExist()
    }

    @Test
    fun testFilter_usingUserData() {
        val forms = arrayOf("Placeholder", "Case Data Filterable Form", "User Filterable Form")
        InstrumentationUtility.login("test_filters_user_data", "123")
        matchCases()

        onView(withText("Select A"))
                .perform(click())

        forms.areDisplayed()

        Espresso.pressBack()
        onView(withText("Select B"))
                .perform(click())

        forms.areDisplayed()

        Espresso.pressBack()
        onView(withText("Select C"))
                .perform(click())

        withText("Placeholder").isDisplayed()
        withText("Case Data Filterable Form").doesNotExist()
        withText("User Filterable Form").isDisplayed()
    }

    @Test
    fun testFilter_usingUserData_withoutCase() {
        InstrumentationUtility.login("test_filter", "123")
        onView(withText("Start"))
                .perform(click())
        onView(withText("Filter Tests"))
                .perform(click())

        withText("Select A").doesNotExist()
        withText("Select B").isDisplayed()
        withText("Select C").isDisplayed()
    }

    private fun matchCases() {
        val cases = arrayOf("Select A", "Select B", "Select C")
        onView(withText("Start"))
                .perform(click())
        onView(withText("Selection Tests"))
                .perform(click())

        cases.areDisplayed()
    }

}
