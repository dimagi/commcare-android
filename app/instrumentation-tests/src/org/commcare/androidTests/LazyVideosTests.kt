package org.commcare.androidTests


import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.commcare.CommCareApplication
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
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
        installApp(APP_NAME, CCZ_NAME)
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

    fun testVideosWithValidReference() {
        InstrumentationUtility.login("test1", "123")
        InstrumentationUtility.openForm(1, 0)
        InstrumentationUtility.waitForView(withId(R.id.video_button))

        onView(CustomMatchers.withDrawable(
                CommCareApplication.instance(),
                R.drawable.update_download_icon
            )
        ).isPresent()

        if (onView(withText(R.string.video_download_prompt)).isPresent()) {
            onView(withId(R.id.video_button)).perform(click())
            onView(withSubstring("Download started")).isPresent()
            InstrumentationUtility.waitForView(withText("Download complete"))
            onView(
                CustomMatchers.withDrawable(
                    CommCareApplication.instance(),
                    android.R.drawable.ic_media_play
                )
            ).isPresent();
        } else {
            InstrumentationUtility.stubIntentWithAction(Intent.ACTION_VIEW)
            onView(withId(R.id.video_button)).perform(click())
            InstrumentationUtility.nextPage()
        }
        Thread.sleep(5000)
        assertTrue(onView(allOf(withId(R.id.inline_video_view))).isPresent())
        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.withDrawable(
                CommCareApplication.instance(),
                R.drawable.icon_info_outline_lightcool
            )
        ).perform(click())
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
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_VIEW)
        onView(withId(R.id.video_button)).perform(click())
        onView(withText("OK")).perform(click())
        InstrumentationUtility.submitForm()
        assertTrue(onView(withText("1 form sent to server!")).isPresent())
        InstrumentationUtility.logout()
    }

    fun testVideosWithNoReferences() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        InstrumentationUtility.login("test1", "123")
        InstrumentationUtility.openForm(1, 1)
        onView(withText("Video file is missing for this question, click the download button above to download")).isPresent()
        onView(
            CustomMatchers.withDrawable(
                CommCareApplication.instance(),
                R.drawable.update_download_icon
            )
        ).isPresent()
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_SEND)
        onView(withId(R.id.video_button)).perform(click())
        InstrumentationUtility.waitForView(withText(R.string.download_complete))
        InstrumentationUtility.waitForView(withText("Media not found in the application"))
        InstrumentationUtility.nextPage()
        assertTrue(onView(withId(R.id.missing_media_view)).isPresent())
        InstrumentationUtility.submitForm()
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())

    }
}
