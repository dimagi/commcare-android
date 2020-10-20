package org.commcare.androidTests

import android.content.res.Resources
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
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
class CaseListSearchTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "test_list_search.ccz"
        const val APP_NAME = "Test: List Searching"
        val entities = arrayOf("Christy", "Matthew", "Steven", "Tuco")
    }

    @Before
    fun setup() {
        // screen_entity_select_list
        //2 of 4 results for your search: "c"
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test_list_search", "123")
    }

    @Test
    fun testSearch() {
        gotoSearchTestModule()

        // Confirm 4 items in the list
        onView(withId(R.id.screen_entity_select_list))
                .check(matches(CustomMatchers.matchListSize(4)))
        matchListItems(entities)

        onView(withId(R.id.search_action_bar))
                .perform(click())

        val searchTextId = Resources.getSystem().getIdentifier("android:id/search_src_text", null, null)

        // type `c` and search
        onView(withId(searchTextId))
                .perform(typeText("c"))
        matchList(2, 4, "c", arrayOf(entities[0], entities[3]))

        // type `h` and search
        onView(withId(searchTextId))
                .perform(typeText("h"))
        matchList(1, 4, "ch", arrayOf(entities[0]))

        // Confirm it persists on rotation
        InstrumentationUtility.rotateLeft()
        Espresso.closeSoftKeyboard()
        matchList(1, 4, "ch", arrayOf(entities[0]))

        InstrumentationUtility.rotatePortrait()
        Espresso.closeSoftKeyboard()
        matchList(1, 4, "ch", arrayOf(entities[0]))

        onView(allOf(withClassName(endsWith("ImageView")), withContentDescription("Clear query")))
                .perform(click())
        onView(withText(containsString("results for your search")))
                .check(matches(not(isDisplayed())))
        matchListItems(entities)

        onView(withText("Search Tests"))
                .check(matches(not(isDisplayed())))
        InstrumentationUtility.gotoHome()

        // Start Over
        gotoSearchTestModule()

        // Clearing searches the other way
        onView(withId(R.id.search_action_bar))
                .perform(click())
        onView(withId(searchTextId))
                .perform(typeText("x"))
        matchList(0, 4, "x", null)
        closeSoftKeyboard()
        // Use back to clear search
        Espresso.pressBack()
        onView(withText(containsString("results for your search")))
                .check(matches(not(isDisplayed())))
        matchListItems(entities)

        onView(withText("Search Tests"))
                .check(matches(isDisplayed()))

        // Fuzzy search
        onView(withId(R.id.search_action_bar))
                .perform(click())
        onView(withId(searchTextId))
                .perform(typeText("kri"))
        matchList(0, 4, "kri", null)

        onView(withId(searchTextId))
                .perform(typeText("sty"))
        matchList(1, 4, "kristy", arrayOf(entities[0]))
    }

    private fun gotoSearchTestModule() {
        onView(withText("Start"))
                .perform(click())
        onView(withText("Search Tests"))
                .perform(click())
    }

    private fun matchList(matched: Int, totalSize: Int, searchText: String, items: Array<String>?) {
        onView(withText("$matched of $totalSize results for your search: \"$searchText\""))
                .check(matches(isDisplayed()))
        if (items != null) {
            matchListItems(items)
        }
    }

    private fun matchListItems(items: Array<String>) {
        for (name in items) {
            onView(allOf(
                    withId(R.id.entity_view_text),
                    withText(name)
            )).check(matches(isDisplayed()))
        }
    }

}