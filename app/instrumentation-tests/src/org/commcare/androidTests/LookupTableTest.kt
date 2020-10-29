package org.commcare.androidTests

import android.widget.RadioButton
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.HQApi
import org.commcare.utils.InstrumentationUtility
import org.commcare.views.widgets.SelectOneWidget
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LookupTableTest: BaseTest() {

    @Test
    fun testLookUpTable_UpdatesOnSync() {
        // Upload lookup table
        HQApi.uploadFixture("initial_cities_table.xlsx")
        installApp("Integration Tests", "integration_test_app.ccz")
        InstrumentationUtility.login("test", "123")
        syncAndGoToLookupTableForm()

        InstrumentationUtility.matchChildCount(SelectOneWidget::class.java, RadioButton::class.java, 1)

        // Upload another lookup table
        HQApi.uploadFixture("extended_cities_table.xlsx")
        pressBack()
        onView(withText(R.string.do_not_save))
                .perform(click())
        pressBack()
        pressBack()
        syncAndGoToLookupTableForm()
        InstrumentationUtility.matchChildCount(SelectOneWidget::class.java, RadioButton::class.java, 7)
    }

    @Test
    fun testLookupTableSorting() {
        installApp("Lookup Table Sorting", "lookup_table_sort_test.ccz")
        InstrumentationUtility.login("test", "123")
        InstrumentationUtility.openModule("Test Forms")
        onView(withText("Lookup Table Select WITHOUT sorting"))
                .perform(click())
        InstrumentationUtility.matchChildCount(SelectOneWidget::class.java, RadioButton::class.java, 7)
        checkOptionsList(arrayOf(
                "Orange",
                "Banana",
                "Plum",
                "Pear",
                "Grape",
                "Kiwi",
                "Apple"
        ))
        InstrumentationUtility.exitForm(R.string.do_not_save)
        onView(withText("Lookup Table Select WITH sorting"))
                .perform(click())
        InstrumentationUtility.matchChildCount(SelectOneWidget::class.java, RadioButton::class.java, 7)
        checkOptionsList(arrayOf(
                "Apple",
                "Banana",
                "Grape",
                "Kiwi",
                "Orange",
                "Pear",
                "Plum"
        ))
    }

    private fun checkOptionsList(arr: Array<String>) {
        var position = 1
        arr.forEach {
            onView(CustomMatchers.find(
                    allOf(withClassName(endsWith("RadioButton"))),
                    position
            )).check(matches(withText(it)))
            position++
        }
    }

    private fun syncAndGoToLookupTableForm() {
        onView(withText("Sync with Server"))
                .perform(click())
        onView(withText("Start"))
                .perform(click())
        onView(withText("Misc"))
                .perform(click())
        onView(withText("Lookup Tables"))
                .perform(click())
    }
}
