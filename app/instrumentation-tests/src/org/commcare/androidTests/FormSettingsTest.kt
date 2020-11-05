package org.commcare.androidTests

import androidx.annotation.IdRes
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class FormSettingsTest: BaseTest() {

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
    fun testSaveFormSetting() {
        InstrumentationUtility.openForm(0, 1)
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("Test Value 123"))
        Espresso.closeSoftKeyboard()
        selectSetting(R.string.save_all_answers)

        Espresso.pressBack()
        onView(withText(R.string.do_not_save))
                .perform(click())
        InstrumentationUtility.gotoHome()

        InstrumentationUtility.openFirstIncompleteForm()
        onView(withText("Test Value 123"))
                .check(matches(isDisplayed()))
    }

    @Test
    fun testGoToPromptSetting() {
        InstrumentationUtility.openForm(0, 1)
        selectSetting(R.string.view_hierarchy)

        // Confirm you're seeing all the options.
        onView(withId(R.id.jumpPreviousButton))
                .check(matches(isDisplayed()))
        onView(withId(R.id.jumpBeginningButton))
                .check(matches(isDisplayed()))
        onView(withId(R.id.jumpEndButton))
                .check(matches(isDisplayed()))

        // Click go to end
        onView(withId(R.id.jumpEndButton))
                .perform(click())

        // Confirm you can see the finish button
        onView(withId(R.id.nav_btn_finish))
                .check(matches(isDisplayed()))
    }

    @Test
    fun testChangeLanguageSetting() {
        InstrumentationUtility.openForm(0, 1 )
        selectSetting(R.string.change_language)
        // We see 2 choices::
        onView(withId(R.id.choices_list_view))
                .check(matches(CustomMatchers.matchListSize(2)))
        // English and Hindi
        onView(withText("English"))
                .check(matches(isDisplayed()))
        onView(withText("हिंदी"))
                .check(matches(isDisplayed()))

        onView(withText("हिंदी"))
                .perform(click())

        onView(withText(startsWith("HINDI TRANSLATION")))
                .check(matches(isDisplayed()))
        selectSetting(R.string.change_language)
        // Again we see 2 choices
        onView(withId(R.id.choices_list_view))
                .check(matches(CustomMatchers.matchListSize(2)))

        onView(withText("English"))
                .perform(click())
        onView(withText(startsWith("HINDI TRANSLATION")))
                .check(doesNotExist())
    }

    @Test
    fun testTextSizeSetting() {
        InstrumentationUtility.openForm(0, 1 )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fontNames = context.resources.getStringArray(R.array.font_size_entries)
        val fontValues = context.resources.getStringArray(R.array.font_size_entry_values)

        // Change font size to extra small and confirm if the question text size changes
        val extraSmallFontIndex = fontNames.size - 1
        changeFontSize(fontNames[extraSmallFontIndex])
        onView(withText(startsWith("The following questions")))
                .check(matches(CustomMatchers.withFontSize(fontValues[extraSmallFontIndex].toFloat())))

        val mediumFontIndex = 2
        changeFontSize(fontNames[mediumFontIndex])
        onView(withText(startsWith("The following questions")))
                .check(matches(CustomMatchers.withFontSize(fontValues[mediumFontIndex].toFloat())))
    }

    private fun selectSetting(@IdRes text: Int) {
        InstrumentationUtility.openOptionsMenu()
        onView(withText(text))
                .perform(click())
    }

    private fun changeFontSize(fontSize: String) {
        selectSetting(R.string.form_entry_settings)
        onView(withText(R.string.font_size))
                .perform(click())
        onView(withText(fontSize))
                .perform(click())
        Espresso.pressBack()
    }
}
