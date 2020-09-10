package org.commcare.androidTests

import android.widget.RadioButton
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.dalvik.R
import org.commcare.utils.HQApi
import org.commcare.utils.InstrumentationUtility
import org.commcare.views.widgets.SelectOneWidget
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LookupTableTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "integration_test_app.ccz"
        const val APP_NAME = "Integration Tests"
    }

    @Test
    fun testLookUpTable_UpdatesOnSync() {
        // Upload lookup table
        HQApi.uploadFixture("initial_cities_table.xlsx")
        installApp(APP_NAME, CCZ_NAME)
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