package org.commcare.androidTests

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import junit.framework.TestCase.assertEquals
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@BrowserstackTests
class ApkDependenciesTest : BaseTest() {

    companion object {
        const val CCZ_NAME = "android_dependency_test.ccz"
        const val APP_NAME = "Android Dependency Test"
        const val PLAY_STORE_URL = "market://details?id=org.commcare.dalvik.reminders"
    }

    @Test
    fun testAppDependenciesCheck() {
        InstrumentationUtility.installApp(CCZ_NAME)
        veirfyDependencyDialog()
        InstrumentationUtility.login("test", "123");
        onView(withText("Start"))
            .perform(click())
        veirfyDependencyDialog()
        verifyDialogDisimissOnBack()

        // mock such that all dependencies are satisfied, and check dialog doesn't appear
        mockkObject(PlaystoreUtils)
        every { PlaystoreUtils.isApkInstalled(any()) } returns true
        onView(withText("Start"))
            .perform(click())
        onView(withText(R.string.dependency_missing_dialog_title)).check(doesNotExist())
        onView(withText("Case List")).isPresent()
    }

    private fun verifyDialogDisimissOnBack() {
        InstrumentationUtility.gotoHome()
        onView(withText("Start"))
            .perform(click())
        onView(withText(R.string.dependency_missing_dialog_title)).isPresent()
        pressBack()
        onView(withText(R.string.dependency_missing_dialog_title)).check(doesNotExist())
    }

    private fun veirfyDependencyDialog() {
        onView(withText(R.string.dependency_missing_dialog_title)).isPresent()
        onView(withText(R.string.dependency_missing_dialog_message)).isPresent()
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_VIEW)
        onView(withText(R.string.dependency_missing_dialog_go_to_store)).perform(click())
        verifyPlaystoreIntent()
    }

    private fun verifyPlaystoreIntent() {
        val receivedIntents = Intents.getIntents()
        receivedIntents.findLast { intent -> intent.action ==  Intent.ACTION_VIEW}.let {
            assertEquals("Incorrect Play store url",PLAY_STORE_URL,it!!.data.toString())
        }
    }
}
