package org.commcare.androidTests

import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anything
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class CaseListSortTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "test_list_search.ccz"
        const val APP_NAME = "Test: List Searching"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test_sort", "123")
    }

    @Test
    fun testSorting() {
        onView(withText("Start"))
                .perform(click())
        onView(withText("Sort Tests"))
                .perform(click())
        matchTextAtPos(0, "Missing")
        matchTextAtPos(1, "Earliest")
        matchTextAtPos(2, "Middle")
        matchTextAtPos(3, "Last")

        clickSortByMenu()
        onView(withText("Name"))
                .perform(click())
        matchTextAtPos(0, "Earliest")
        matchTextAtPos(1, "Last")
        matchTextAtPos(2, "Middle")
        matchTextAtPos(3, "Missing")

        clickSortByMenu()
        onView(withText("(^) Name"))
                .perform(click())
        matchTextAtPos(0, "Missing")
        matchTextAtPos(1, "Middle")
        matchTextAtPos(2, "Last")
        matchTextAtPos(3, "Earliest")
    }

    private fun clickSortByMenu() {
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Sort By..."))
                .perform(click())
    }

    private fun matchTextAtPos(position: Int, text: String) {
        onData(anything())
                .inAdapterView(withId(R.id.screen_entity_select_list))
                .atPosition(position)
                .onChildView(allOf(
                        withId(R.id.entity_view_text),
                        withText(text)
                ))
                .check(matches(isDisplayed()))
    }

}