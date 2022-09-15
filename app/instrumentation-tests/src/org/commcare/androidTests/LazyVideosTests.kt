package org.commcare.androidTests


import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.commcare.CommCareApplication
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class LazyVideosTests : BaseTest() {

    companion object {
        const val CCZ_NAME = "lazy_videos_tests.ccz"
        const val APP_NAME = "Lazy Videos"
    }

    @Before
    fun setup() {
        InstrumentationUtility.uninstallCurrentApp()
        InstrumentationUtility.installApp(CCZ_NAME)
        Espresso.pressBack()
        updateApp()

    }

    @After
    fun tearDown() {
        InstrumentationUtility.logout()
    }

    @Test
    fun testVideosWithReferences() {
        testVideosWithValidReference()
        testVideosWithNoReferences()
    }

    private fun updateApp(){
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Go To App Manager"))
            .perform(click())
        onView(withText(APP_NAME)).perform(click())
        onView(withText("Update"))
            .perform(click())
        InstrumentationUtility.waitForView(withSubstring("Update to version"))
        gotoLogin()
    }

    private fun gotoLogin() {
        for (i in 0..2) { // Try atmost 3 times.
            if (onView(withId(R.id.edit_username)).isPresent()) {
                return
            } else {
                Espresso.pressBack()
            }
        }
    }
    private fun testVideosWithValidReference() {
        InstrumentationUtility.login("test1", "123")
        InstrumentationUtility.openForm(1, 0)
        InstrumentationUtility.waitForView(withId(R.id.video_button))
        if ((onView(withTagValue(CoreMatchers.`is`(R.drawable.update_download_icon))).isPresent())
            or (onView(withText(R.string.video_download_prompt)).isPresent())) {
            InstrumentationUtility.stubIntentWithAction(Intent.ACTION_SEND)
            onView(withTagValue(CoreMatchers.`is`(R.drawable.update_download_icon))).perform(click())
            onView(withId(R.id.video_button)).perform(click())
            InstrumentationUtility.waitForView(withSubstring("Download in Progress"))
            InstrumentationUtility.waitForView(withSubstring("Download complete"))
            InstrumentationUtility.waitForView(withTagValue(CoreMatchers.`is`(android.R.drawable.ic_media_play)))
        }
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_VIEW)
        onView(withId(R.id.video_button)).perform(click())
        InstrumentationUtility.nextPage()
        Thread.sleep(5000)
        assertTrue(onView(allOf(withId(R.id.inline_video_view))).isPresent())
        InstrumentationUtility.nextPage()
        InstrumentationUtility.waitForView(CustomMatchers.withDrawable(
            CommCareApplication.instance(),
            R.drawable.icon_info_outline_lightcool
        ))

        onView(
            CustomMatchers.withDrawable(
                CommCareApplication.instance(),
                R.drawable.icon_info_outline_lightcool
            )
        ).perform(click())
        onView(withTagValue(CoreMatchers.`is`(android.R.drawable.ic_media_play))).check(matches(isDisplayed()))
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_VIEW)
        onView(withId(R.id.video_button)).perform(click())
        onView(withText("OK")).perform(click())
        InstrumentationUtility.nextPage()

        onView(
            CustomMatchers.withDrawable(
                CommCareApplication.instance(),
                R.drawable.icon_info_outline_lightcool
            )
        ).perform(click())
        onView(withTagValue(CoreMatchers.`is`(android.R.drawable.ic_media_play))).check(matches(isDisplayed()))
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_VIEW)
        onView(withId(R.id.video_button)).perform(click())
        onView(withText("OK")).perform(click())
        InstrumentationUtility.submitForm()
        assertTrue(onView(withText("1 form sent to server!")).isPresent())
        InstrumentationUtility.logout()
    }

    private fun testVideosWithNoReferences() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        InstrumentationUtility.login("test1", "123")
        InstrumentationUtility.openForm(1, 1)
        InstrumentationUtility.waitForView(withText(R.string.video_download_prompt))
        onView(withTagValue(CoreMatchers.`is`(R.drawable.update_download_icon))).check(matches(isDisplayed()))
        onView(withId(R.id.video_button)).perform(click())
        InstrumentationUtility.waitForView(withText(R.string.download_complete))
        InstrumentationUtility.waitForView(withText("Media not found in the application"))
        InstrumentationUtility.nextPage()
        assertTrue(onView(withId(R.id.missing_media_view)).isPresent())
        InstrumentationUtility.submitForm()
        assertTrue(onView(withText("1 form sent to server!")).isPresent())
    }
}
