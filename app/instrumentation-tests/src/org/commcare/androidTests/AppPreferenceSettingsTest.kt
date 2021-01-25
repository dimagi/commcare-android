package org.commcare.androidTests

import android.app.Instrumentation
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class AppPreferenceSettingsTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "settings_sheet_tests.ccz"
        const val APP_NAME = "App for Settings Sheet"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("settings.test", "123")
    }

    @Test
    fun testFuzzySearchSetting() {
        val settingName = "Fuzzy Search Matches"
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
                .perform(click())
        selectSetting(settingName)

        onView(withText("Enabled"))
                .check(matches(isDisplayed()))
        onView(withText("Disabled"))
                .check(matches(isDisplayed()))

        // Check enabled is selected by default
        matchSelectedPreference("Enabled")

        // Change setting and check their persistence on rotation
        changePreferenceAndCheckPersistence("Disabled", settingName)
        changePreferenceAndCheckPersistence("Enabled", settingName)

        cancelSettingChangeDialog()
    }

    @Test
    fun testUpdateFrequencySetting() {
        val settingName = "Auto Update Frequency"
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
                .perform(click())
        selectSetting(settingName)

        onView(withText("Never"))
                .check(matches(isDisplayed()))
        onView(withText("Daily"))
                .check(matches(isDisplayed()))
        onView(withText("Weekly"))
                .check(matches(isDisplayed()))

        // Change setting and check their persistence
        changePreferenceAndCheckPersistence("Daily", settingName)
        changePreferenceAndCheckPersistence("Weekly", settingName)
        changePreferenceAndCheckPersistence("Never", settingName)

        cancelSettingChangeDialog()
    }

    @Test
    fun testPrintTemplateSetting() {
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
                .perform(click())
        selectSetting("Set Print Template")

        //Create a dummy file selection intent
        val resultData = Intent()
        val expectedIntent = CustomMatchers.withIntent(Intent.ACTION_GET_CONTENT, Intent.CATEGORY_OPENABLE, "*/*")
        val result = Instrumentation.ActivityResult(AppCompatActivity.RESULT_CANCELED, resultData)
        intending(expectedIntent).respondWith(result)

        // Click on file fetch
        onView(withId(R.id.filefetch))
                .perform(click())

        // Confirm that the file selection intent was called.
        intended(expectedIntent)
    }

    private fun selectSetting(text: String) {
        onView(withText("CommCare > Settings"))
                .check(matches(isDisplayed()))
        onView(withId(R.id.recycler_view))
                .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                        hasDescendant(withText(text))
                ))
        onView(withText(text))
                .perform(click())
    }

    private fun cancelSettingChangeDialog() {
        onView(withText("CANCEL"))
                .perform(click())
        onView(withText("CommCare > Settings"))
                .check(matches(isDisplayed()))
    }

    private fun changePreferenceAndCheckPersistence(newPref: String, setting: String) {
        // Change to new preference
        onView(withText(newPref))
                .perform(click())

        //Rotate and check the selection persists.
        InstrumentationUtility.rotateLeft()
        selectSetting(setting)
        matchSelectedPreference(newPref)

        // Rotate back to portrait
        InstrumentationUtility.rotatePortrait()
    }

    private fun matchSelectedPreference(value: String) {
        onView(allOf(
                withClassName(endsWith("AppCompatCheckedTextView")),
                withText(value)
        )).check(matches(isChecked()))
    }

}
