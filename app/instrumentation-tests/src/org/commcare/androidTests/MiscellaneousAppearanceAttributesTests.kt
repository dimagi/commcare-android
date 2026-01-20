package org.commcare.androidTests


import android.graphics.Color
import android.util.Log
import android.view.KeyCharacterMap
import android.view.inputmethod.EditorInfo
import android.widget.DatePicker
import android.widget.ProgressBar
import android.widget.TimePicker
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.PositionAssertions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.commcare.CommCareInstrumentationTestApplication
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.CustomMatchers.isPasswordHidden
import org.commcare.utils.CustomMatchers.withImageBgColor
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
import org.hamcrest.Matchers
import org.hamcrest.Matchers.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Math.abs
import java.lang.Math.round
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream.range


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class MiscellaneousAppearanceAttributesTests : BaseTest() {

    companion object {
        const val CCZ_NAME = "basic_tests.ccz"
        const val APP_NAME = "Basic Tests"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test1", "123")
    }

//    @After
//    fun tearDown() {
//        InstrumentationUtility.logout()
//    }


    @Test
    fun testAppearanceAttributes(){
        InstrumentationUtility.openForm(0,12)
        InstrumentationUtility.nextPage()
        assertTrue(onView(withText("This is the hint text.")).isPresent())
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("Test for hint text"))
        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageButton"))),
                4
            )).perform(click())
//        onView(withTagValue(CoreMatchers.`is`(R.drawable.icon_info_fill_lightcool))).perform(click())
        assertTrue(onView(withText("This is a help message.")).isPresent())
        onView(withText("OK")).perform(click())
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("Test for help message"))
        InstrumentationUtility.nextPage()
        onView(withText("OK. Please continue.")).perform(click())
        InstrumentationUtility.nextPage()
        //skipping this question as it involves capturing GPS location
        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            )).perform(typeText("Edit text 1"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            )).perform(pressImeActionButton())

        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            ))
            .perform(typeTextIntoFocusedView("22"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            )).perform(pressImeActionButton())

        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
            .perform(typeText("33"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_DONE))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            )).perform(pressImeActionButton())
        InstrumentationUtility.nextPage()
        //skipping this question as it requires recording audio
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("Spinner"))).perform(click())
//        onData(anything())
//            .inAdapterView(allOf(is(instanceOf(String.class))))
//            .atPosition(2)
//            .perform(click())
        onView(withText("green")).inRoot(isPlatformPopup()).perform(click())
        InstrumentationUtility.nextPage()
        onView(withText("SELECT ANSWER")).perform(click())
        onView(withText("Green")).perform(click())
        onView(withText("Blue")).perform(click())
        onView(withText("Red")).perform(click())
        onView(withText("Green"))
            .check(matches(isChecked()))
        onView(withText("Blue"))
            .check(matches(isChecked()))
        onView(withText("OK")).perform(click())
        InstrumentationUtility.nextPage()
        //will automate later
        onView(withClassName(endsWith("Combobox")))
            .perform(scrollTo(),typeText("App"))
        assertTrue(
            onView(withText("Apple"))
                .inRoot(isPlatformPopup())
                .isPresent())
        assertTrue(
            onView(withText("Apply"))
                .inRoot(isPlatformPopup())
                .isPresent())
        onView(withClassName(endsWith("Combobox")))
            .perform(scrollTo(),clearText(), typeText("C"))
        assertTrue(
            onView(withText("Cry"))
                .inRoot(isPlatformPopup())
                .isPresent())
        assertTrue(
            onView(withText("Crate"))
                .inRoot(isPlatformPopup())
                .isPresent())
        onView(withClassName(endsWith("Combobox")))
            .perform(scrollTo(),clearText(), typeText("Cra"))
        assertTrue(
            onView(withText("Crate"))
                .inRoot(isPlatformPopup())
                .isPresent())
        InstrumentationUtility.nextPage()
        assertTrue(onView(withText("The text entered is not a valid answer choice")).isPresent())
        onView(withClassName(endsWith("Combobox")))
            .perform(scrollTo(),click())
        onView(withText("Crate"))
            .inRoot(isPlatformPopup())
            .perform(click())
        InstrumentationUtility.nextPage()
        onView(withId(R.id.nav_btn_prev)).perform(click())
        onView(withClassName(endsWith("Combobox")))
            .perform(scrollTo())
            .check(matches(withText("Crate")))
        onView(withClassName(endsWith("Combobox")))
            .perform(typeText("z"))
        onView(withClassName(endsWith("Combobox")))
            .check(matches(not(withText("Craz"))))
        onView(withClassName(endsWith("Combobox")))
                .perform(clearText(), typeText("balloon"))
        assertTrue(
            onView(withText("Balloon"))
                .inRoot(isPlatformPopup())
                .isPresent())
        InstrumentationUtility.nextPage()
        onView(withId(R.id.nav_btn_prev)).perform(click())
        onView(withClassName(endsWith("Combobox")))
            .perform(scrollTo())
            .check(matches(withText("Balloon")))
        onView(withClassName(endsWith("Combobox")))
            .perform(clearText())
        InstrumentationUtility.nextPage()
        onView(withId(R.id.nav_btn_prev)).perform(click())
        onView(withClassName(endsWith("Combobox")))
            .perform(scrollTo())
        onView(withClassName(endsWith("Combobox")))
            .check(matches(withText("")))
        InstrumentationUtility.nextPage()

        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("Combobox")))
            .perform(typeText("App"))
        assertTrue(
            onView(withText("Apple is a fruit"))
                .inRoot(isPlatformPopup())
                .isPresent())
        assertTrue(
            onView(withText("Applicant option"))
                .inRoot(isPlatformPopup())
                .isPresent())
        assertTrue(
            onView(withText("Apply the filters"))
                .inRoot(isPlatformPopup())
                .isPresent())
        onView(withClassName(endsWith("Combobox")))
            .perform(clearText(), typeText("Cr"))
        assertTrue(
            onView(withText("Crate"))
                .inRoot(isPlatformPopup())
                .isPresent())
        assertTrue(
            onView(withText("Crash"))
                .inRoot(isPlatformPopup())
                .isPresent())
        assertTrue(
            onView(withText("Crew member"))
                .inRoot(isPlatformPopup())
                .isPresent())
        onView(withText("Crew member"))
            .inRoot(isPlatformPopup())
            .perform(click())
        InstrumentationUtility.nextPage()
        onView(withId(R.id.nav_btn_prev)).perform(click())
        onView(withClassName(endsWith("Combobox")))
            .check(matches(withText("Crew member")))

        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("Combobox")))
            .perform(typeText("Aply"))
        assertTrue(
            onView(withText("Apply the filters"))
                .inRoot(isPlatformPopup())
                .isPresent())
        onView(withClassName(endsWith("Combobox")))
            .perform(clearText(), typeText("Ctas"))
        assertTrue(
            onView(withText("Crash"))
                .inRoot(isPlatformPopup())
                .isPresent())
        onView(withText("Crash"))
            .inRoot(isPlatformPopup())
            .perform(click())
        InstrumentationUtility.nextPage()
        onView(withId(R.id.nav_btn_prev)).perform(click())
        onView(withClassName(endsWith("Combobox")))
            .check(matches(withText("Crash")))
        InstrumentationUtility.nextPage()
        onView(allOf(
            withClassName(endsWith("RadioButton")),
            hasSibling(withText("Yes"))
        )).perform(click())
        onView(allOf(
            withClassName(endsWith("RadioButton")),
            hasSibling(withText("No"))
        )).perform(click())
        onView(allOf(
            withClassName(endsWith("RadioButton")),
            hasSibling(withText("Yes"))
        )).check(matches(isNotSelected()))
        InstrumentationUtility.nextPage()

        onView(allOf(
            withClassName(endsWith("CheckBox")),
            hasSibling(withText("one"))
        )).perform(click())
        onView(allOf(
            withClassName(endsWith("CheckBox")),
            hasSibling(withText("two"))
        )).perform(click())
        onView(allOf(
            withClassName(endsWith("CheckBox")),
            hasSibling(withText("three"))
        )).perform(click())
        onView(allOf(
            withClassName(endsWith("CheckBox")),
            hasSibling(withText("one"))
        )).check(matches(isChecked()))
        onView(allOf(
            withClassName(endsWith("CheckBox")),
            hasSibling(withText("two"))
        )).check(matches(isChecked()))
        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                1
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                2
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                3
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                4
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                5
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                6
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                1
            )).check(matches(isChecked()))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                2
            )).check(matches(isChecked()))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                3
            )).check(matches(isChecked()))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                4
            )).check(matches(isChecked()))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("CheckBox"))),
                5
            )).check(matches(isChecked()))
        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("RadioButton"))),
                1
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("RadioButton"))),
                2
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("RadioButton"))),
                1
            )).check(matches(isNotSelected()))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("RadioButton"))),
                3
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("RadioButton"))),
                4
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("RadioButton"))),
                3
            )).check(matches(isNotSelected()))
        InstrumentationUtility.nextPage()
        onView(allOf(withClassName(endsWith("RadioButton")),
        withText("green"))).perform(click())
        assertTrue(onView(allOf(
            withId(R.id.form_entry_label_layout),
            hasDescendant(withText("Green label"))
        )).isPresent())
        onView(withText("CLEAR")).perform(click())
        onView(allOf(withClassName(endsWith("RadioButton")),
            withText("yellow"))).perform(click())
        assertTrue(onView(allOf(
            withId(R.id.form_entry_label_layout),
            hasDescendant(withText("Yellow label"))
        )).isPresent())
        onView(withText("CLEAR")).perform(click())
        onView(allOf(withClassName(endsWith("RadioButton")),
            withText("red"))).perform(click())
        assertTrue(onView(allOf(
            withId(R.id.form_entry_label_layout),
            hasDescendant(withText("Red label"))
        )).isPresent())
        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                4)
            ).perform(click())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                5)
        ).perform(click())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                6
            )).perform(click())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                7
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                4
            )).check(matches(withImageBgColor(Color.parseColor("#FFFF8C00"))))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                5
            )).check(matches(withImageBgColor(Color.parseColor("#FFFF8C00"))))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                6
            )).check(matches(withImageBgColor(Color.parseColor("#FFFF8C00"))))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                7
            )).check(matches(withImageBgColor(Color.parseColor("#FFFF8C00"))))
        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                4)
        ).perform(click())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                5)
        ).perform(click())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                6
            )).perform(click())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                7
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                4
            )).check(matches(withImageBgColor(Color.WHITE)))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                5
            )).check(matches(withImageBgColor(Color.WHITE)))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                6
            )).check(matches(withImageBgColor(Color.WHITE)))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                7
            )).check(matches(withImageBgColor(Color.parseColor("#FFFF8C00"))))
        InstrumentationUtility.nextPage()
        //skipping this question as it requires long pressing text
        InstrumentationUtility.nextPage()
        //skipping this question as it requires scanning barcode
        InstrumentationUtility.nextPage()
        InstrumentationUtility.nextPage()
        assertTrue(onView(withText("Sorry, this response is required!")).isPresent())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                4)
        ).perform(click())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                5)
        ).perform(click())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                6
            )).perform(click())
        onView(
            CustomMatchers.find(withClassName(endsWith("ImageView")),
                7
            )).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                4
            )).check(matches(withImageBgColor(Color.WHITE)))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                5
            )).check(matches(withImageBgColor(Color.WHITE)))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                6
            )).check(matches(withImageBgColor(Color.WHITE)))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                7,

            )).check(matches(withImageBgColor(Color.parseColor("#FFFF8C00"))))
        InstrumentationUtility.nextPage()
        onView(withText("onward")).perform(click())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("ImageView"))),
                4
            )).perform(click())

        //skipping this question as it requires taking picture and recording video
        InstrumentationUtility.nextPage()
        onView(CustomMatchers.find(
            withClassName(endsWith("EditText")),1))
            .check(isCompletelyRightOf(withText("Enter a decimal")))
        onView(CustomMatchers.find(
            withClassName(endsWith("EditText")),2))
            .check(isCompletelyRightOf(withText("Enter an integer")))

        onView(CustomMatchers.find(
            withClassName(endsWith("EditText")),1))
            .perform(typeText("11.5"))
        onView(CustomMatchers.find(
            withClassName(endsWith("EditText")),2))
            .perform(typeText("21"))
        InstrumentationUtility.nextPage()
        InstrumentationUtility.sleep(5)
        assertTrue(onView(allOf(withId(R.id.inline_video_view))).isPresent())
        InstrumentationUtility.nextPage()
        InstrumentationUtility.submitForm()
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())


    }


}
