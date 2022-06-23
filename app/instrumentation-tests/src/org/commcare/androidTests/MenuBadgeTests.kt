package org.commcare.androidTests

import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.annotations.BrowserstackTests
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.InstrumentationUtility.waitForView
import org.commcare.utils.isPresent

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith



@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class MenuBadgeTests: BaseTest() {
    companion object {
        const val CCZ_NAME = "menu_badge_performance_testing.ccz"
        const val APP_NAME = "App for Menu Badge testing"

    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("case_load", "123")
    }

    @Test
    fun testMenuBadge(){
        onView(withText("Start"))
            .perform(ViewActions.click())
        assertTrue(onView(withText("Child Management (Update)")).isPresent())
        onView(isRoot()).perform(waitForView(withText("553")))
        InstrumentationUtility.logout()
    }

}
