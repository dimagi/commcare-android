package org.commcare.androidTests

import android.widget.ListView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.Assert.fail
import org.commcare.CommCareApplication
import org.commcare.CommCareInstrumentationTestApplication
import org.commcare.activities.InstallFromListActivity
import org.commcare.android.database.global.models.AppAvailableToInstall
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.hamcrest.Description
import org.hamcrest.Matchers.*
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class InstallFromListTest: BaseTest() {

    @Before
    fun setup() {
        InstrumentationUtility.openOptionsMenu()
        onView(withText("See Apps for My User"))
                .perform(click())
        // Confirm the activity
        intended(hasComponent(InstallFromListActivity::class.java.name))

        // Verify that we start out in mobile user auth mode
        onView(withId(R.id.edit_domain))
                .check(matches(isDisplayed()))
        onView(withId(R.id.edit_email))
                .check(matches(not(isDisplayed())))

        // Toggle switch
        onView(withClassName(endsWith("Switch")))
                .perform(click())

        // Verify that we're now in web user auth mode
        onView(withId(R.id.edit_domain))
                .check(matches(not(isDisplayed())))
        onView(withId(R.id.edit_email))
                .check(matches(isDisplayed()))
    }

    @After
    fun tearDown() {
        // Uninstall app.
        InstrumentationUtility.uninstallCurrentApp()
        InstrumentationUtility.openOptionsMenu()
        onView(withText("See Apps for My User"))
                .perform(click())
        InstrumentationUtility.openOptionsMenu()
        onView(withText("See Apps for Another User"))
                .perform(click())
    }

    @Test
    fun testAppInstall_usingMobieWorkerDetails() {
        // Switch back to mobile auth view
        onView(withClassName(endsWith("Switch")))
                .perform(click())

        // Test getting app list for a mobile user
        InstrumentationUtility.enterText(R.id.edit_username, "test")
        InstrumentationUtility.enterText(R.id.edit_domain, "commcare-tests")
        InstrumentationUtility.enterText(R.id.edit_password, "123")
        onView(withId(R.id.get_apps_button))
                .perform(click())

        // Check that all the apps belong to commcare-tests domain
        for (position in 0 until getAppListSize()) {
            InstrumentationUtility.getSubViewInListItem(R.id.apps_list_view, position, R.id.domain)
                    .check(matches(withText("commcare-tests")))
        }

        // Check the app names
        matchAppInAppList("Case callout test for Simprints")
        matchAppInAppList("Case Search and Claim")
        matchAppInAppList("Integration Tests")

        // Install 1 of the apps
        onData(allOf(`is`(instanceOf(AppAvailableToInstall::class.java)),
                CustomMatchers.withAppName("Case Search and Claim")))
                .perform(click())

        assertAppInstalled("Case Search and Claim")
    }

    @Test
    fun testAppInstall_usingWebWorkerDetails() {
        // Test getting app list for a mobile user
        InstrumentationUtility.enterText(R.id.edit_email, BuildConfig.HQ_API_USERNAME)
        InstrumentationUtility.enterText(R.id.edit_password, BuildConfig.HQ_API_PASSWORD)
        onView(withId(R.id.get_apps_button))
                .perform(click())

        // Check that we see each of the apps in this domain, plus the domain name
        for (position in 0 until getAppListSize()) {
            InstrumentationUtility.getSubViewInListItem(R.id.apps_list_view, position, R.id.domain)
                    .check(matches(anyOf(withText("commcare-tests"), withText("swat"))))
        }

        matchAppInAppList("Case callout test for Simprints")
        matchAppInAppList("Case Search and Claim")
        matchAppInAppList("Demo - Form Design Patterns")
        matchAppInAppList("Demo - Live XForm Examples")
        matchAppInAppList("SWAT: CommCare Projects Phone Survey")
        matchAppInAppList("SWAT: App Tracker")

        // Install 1 of the apps
        onData(allOf(`is`(instanceOf(AppAvailableToInstall::class.java)),
                CustomMatchers.withAppName("SWAT: App Tracker")))
                .perform(click())

        assertAppInstalled("SWAT: App Tracker")
    }

    private fun matchAppInAppList(appName: String) {
        onData(allOf(`is`(instanceOf(AppAvailableToInstall::class.java)),
                CustomMatchers.withAppName(appName)))
                .check(matches(isDisplayed()))
    }

    private fun getAppListSize(): Int {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as CommCareInstrumentationTestApplication
        var activity = application.currentActivity as InstallFromListActivity<*>
        val listView = activity.findViewById<ListView>(R.id.apps_list_view)
        return listView.adapter.count
    }

    private fun assertAppInstalled(appName: String) {
        assert(CommCareApplication.instance().currentApp != null, "App is null")
        assert(CommCareApplication.instance().currentApp.appRecord.displayName == appName, "App didn't match")
    }
}
/**
 * A workaround to Failed resolution of: Lkotlin/_Assertions;
 * This will fail the test if the value is false.
 */
public fun assert(value: Boolean, failMsg: String) {
    if (!value) {
        fail("Assertion Failed: $failMsg")
    }
}