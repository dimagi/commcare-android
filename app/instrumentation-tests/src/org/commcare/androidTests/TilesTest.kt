package org.commcare.androidTests

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.commcare.CommCareInstrumentationTestApplication
import org.commcare.activities.EntitySelectActivity
import org.commcare.dalvik.R
import org.commcare.utils.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class TilesTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "tiles.ccz"
        const val APP_NAME = "Case Tile Tests"
        var caseDetails = arrayOf("Sex F", "Date: 01/07/17")
        var caseDetailsExpanded = caseDetails + arrayOf("Secret")
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("tile.test", "123")
    }

    @Test
    fun testNoTile() {
        InstrumentationUtility.openModule("NoPersist")

        caseDetails.areDisplayed()

        onView(withText("Sally Ride"))
                .perform(click())
        withId(R.id.com_tile_holder_btn_open).doesNotExist()
        withText("Sex F").doesNotExist()

        onView(withText("Continue"))
                .perform(click())
        withText("Sally Ride").doesNotExist()
        onView(withText("Placeholder"))
                .perform(click())

        withText("Inside").isDisplayed()
        withText("Sally Ride").doesNotExist()
    }

    @Test
    fun testPersistentTile_withDropDown() {
        InstrumentationUtility.openModule("PersistentInline")

        onView(withText("Sally Ride"))
                .perform(click())

        caseDetails.areDisplayed()
        withText("Placeholder").isDisplayed()
        withId(R.id.com_tile_holder_btn_open).isDisplayed()

        // Expand tile
        onView(withId(R.id.com_tile_holder_btn_open))
                .perform(click())
        caseDetailsExpanded.areDisplayed()

        // Close expanded list using the same button
        onView(withId(R.id.com_tile_holder_btn_open))
                .perform(click())
        withText("Secret").doesNotExist()

        // Use back button to close the expanded list
        onView(withId(R.id.com_tile_holder_btn_open))
                .perform(click())
        caseDetailsExpanded.areDisplayed()
        Espresso.pressBack()
        caseDetails.areDisplayed()
        withText("Secret").doesNotExist()

        // Confirm on pressing back we're in EntitySelectActivity
        Espresso.pressBack()
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as CommCareInstrumentationTestApplication
        var activity = application.currentActivity
        InstrumentationUtility.assert(activity is EntitySelectActivity, "Current Activity is ${activity.localClassName}")

        onView(withText("Sally Ride"))
                .perform(click())
        caseDetails.areDisplayed()
        withId(R.id.com_tile_holder_btn_open).isDisplayed()

        // Go to form screen
        onView(withText("Placeholder"))
                .perform(click())

        withText("Inside").isDisplayed()
        caseDetails.areDisplayed()

        // Expand tile
        onView(withId(R.id.com_tile_holder_btn_open))
                .perform(click())
        caseDetailsExpanded.areDisplayed()

        //Close expanded list
        onView(withId(R.id.com_tile_holder_btn_open))
                .perform(click())
        caseDetails.areDisplayed()
        withText("Secret").doesNotExist()
        withId(R.id.com_tile_holder_btn_open).isDisplayed()
    }

    @Test
    fun testPersistentTile_withDetail() {
        InstrumentationUtility.openModule("PersistentWithDetail")

        // Clicking this opens all the case details including the expanded ones.
        onView(withText("Sally Ride"))
                .perform(click())
        caseDetailsExpanded.areDisplayed()

        // And we don't see the expand button
        withId(R.id.com_tile_holder_btn_open).isNotDisplayed()

        onView(withText("Continue"))
                .perform(click())

        caseDetails.areDisplayed()
        withText("Placeholder").isDisplayed()
        withId(R.id.com_tile_holder_btn_open).isNotDisplayed()

        // Open form
        onView(withText("Placeholder"))
                .perform(click())

        withText("Inside").isDisplayed()
        // We can still see the case details.
        caseDetails.areDisplayed()
        withId(R.id.com_tile_holder_btn_open).isNotDisplayed()
    }

    @Test
    fun testPersistentTile_noDetail_noInline() {
        InstrumentationUtility.openModule("PersistentNoDetailNoInline")

        onView(withText("Sally Ride"))
                .perform(click())
        caseDetails.areDisplayed()
        withText("Placeholder").isDisplayed()
        withText("Secret").doesNotExist()
        withId(R.id.com_tile_holder_btn_open).isNotDisplayed()

        // go to form and you can still see the case details.
        onView(withText("Placeholder"))
                .perform(click())

        withText("Inside").isDisplayed()
        caseDetails.areDisplayed()
        withText("Secret").doesNotExist()
        withId(R.id.com_tile_holder_btn_open).isNotDisplayed()
    }

    @Test
    fun testBreadcrumb() {
        InstrumentationUtility.openModule("Breadcrumb")

        onView(withText("Sally Ride"))
                .perform(click())

        withText("Sally Ride").isDisplayed()
        withText("Placeholder").isDisplayed()
        caseDetailsExpanded.areGone()
        withId(R.id.com_tile_holder_btn_open).isNotDisplayed()

        onView(withText("Placeholder"))
                .perform(click())

        withText("Sally Ride").isDisplayed()
        withText("Inside").isDisplayed()
        caseDetailsExpanded.areGone()
        withId(R.id.com_tile_holder_btn_open).isNotDisplayed()
    }


}
