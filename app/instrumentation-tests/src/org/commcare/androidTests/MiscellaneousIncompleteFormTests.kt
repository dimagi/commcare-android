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
class MiscellaneousIncompleteFormTests : BaseTest() {

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
    fun testIncompleteForms(){
        InstrumentationUtility.openForm(0,1)
        onView(withText("OK. Please continue.")).perform(click())
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("Try!&g different v@lues# 123"))
        saveAndOpenIncompleteForm()
        onView(withText("OK. Please continue."))
            .check(matches(isChecked()))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .check(matches(
                withText("Try!&g different v@lues# 123")))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("123"))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("21.0"))
        saveAndOpenIncompleteForm()
        onView(withText("OK. Please continue."))
            .check(matches(isChecked()))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .check(matches(
            withText("Try!&g different v@lues# 123")))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .check(matches(
                withText("123")))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .check(matches(
                withText("21")))
        InstrumentationUtility.nextPage()
        onView(withId(R.id.nav_btn_prev))
            .perform(click())
        onView(withClassName(endsWith("EditText")))
            .check(matches(withText("21")))
        InstrumentationUtility.nextPage()
        setDateTo(-10)
        InstrumentationUtility.sleep(1)
        InstrumentationUtility.nextPage()
        setDateTo(-10)
        setTimeTo(11,11)
        InstrumentationUtility.nextPage()
        onView(withText("One")).perform(click())
        onView(withText("Two")).perform(click())
        onView(withText("Three")).perform(click())
        onView(withText("One")).check(matches(isChecked()))
        onView(withText("Two")).check(matches(isChecked()))

        InstrumentationUtility.nextPage()
        onView(withText("One")).perform(click())
        onView(withText("CLEAR")).perform(click())
        onView(withText("One")).check(matches(isNotSelected()))
        onView(withText("Two")).perform(click())
        InstrumentationUtility.nextPage()

        setTimeTo(21,1)
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("Abc123"))
        onView(withClassName(endsWith("EditText")))
            .check(matches(isPasswordHidden()))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("Abc123"))
        onView(withClassName(endsWith("EditText")))
            .check(matches(isPasswordHidden()))
        InstrumentationUtility.nextPage()
        assertTrue(onView(withText("Sorry, this response is invalid!")).isPresent())
        onView(withClassName(endsWith("EditText")))
            .perform(clearText(), typeText("123"))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(clearText(), typeText("12300000"))
        InstrumentationUtility.nextPage()
        onView(withId(R.id.nav_btn_prev))
            .perform(click())
        onView(withClassName(endsWith("EditText"))).check(matches(withText("12300000")))
        onView(withId(R.id.nav_btn_prev))
            .perform(click())
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText"))).check(matches(withText("12300000")))
        InstrumentationUtility.nextPage()
        //skipping this question as it requires signature capture
        InstrumentationUtility.nextPage()
        //skipping this question as it requires barcode scanning
        InstrumentationUtility.nextPage()
        onView(withText("OK. Please continue.")).perform(click())
        InstrumentationUtility.nextPage()
        //skipping this question as it involves capturing GPS location
        InstrumentationUtility.nextPage()
        //skipping this question as it requires capturing image
        InstrumentationUtility.nextPage()
        //skipping this question as it requires recording audio
        InstrumentationUtility.nextPage()
        //skipping this question as it requires recording video
        InstrumentationUtility.nextPage()
        //skipping this question as it requires recording audio and playing it
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("Try!&g different v@lues# 123"))
        InstrumentationUtility.nextPage()
        onView(withText("One")).perform(click())
        onView(withText("Two")).perform(click())
        onView(withText("Three")).perform(click())
        onView(withText("One")).check(matches(isChecked()))
        onView(withText("Two")).check(matches(isChecked()))
        InstrumentationUtility.nextPage()
        onView(withText("One")).perform(click())
        onView(withText("CLEAR")).perform(click())
        onView(withText("One")).check(matches(isNotSelected()))
        onView(withText("Two")).perform(click())
        InstrumentationUtility.nextPage()
        onView(allOf(withClassName(endsWith("RadioButton")),
            withText("Tarragon")
        ))
            .perform(click())
//        onView(withSubstring("checkbox lookup table")).perform(scrollTo())


        onView(allOf(withClassName(endsWith("RadioButton")),
            withText("Tarragon"))).perform(click())
        onView(allOf(withClassName(endsWith("RadioButton")),
            withText("Carrots"))).perform(click())
        onView(allOf(withClassName(endsWith("RadioButton")),
            withText("Tarragon"))).check(matches(isNotSelected()))
        onView(withText("CLEAR")).perform(scrollTo(), click())
        onView(allOf(withClassName(endsWith("RadioButton")),
            withText("Green_Beans"))).perform(click())

        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Tarragon"))).perform(scrollTo(),click())
        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Carrots"))).perform(scrollTo(),click())
        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Green_Beans"))).perform(scrollTo(),click())
        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Tarragon")))
            .perform(scrollTo())
            .check(matches(isChecked()))
        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Carrots")))
            .perform(scrollTo())
            .check(matches(isChecked()))
        InstrumentationUtility.nextPage()
        onView(withText("DO NOT ADD")).perform(click())
        InstrumentationUtility.submitForm()
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())



    }

    @Test
    fun testIncompleteFormSurvey(){
        val formText = "Incomplete Surver Test"
        InstrumentationUtility.openForm(0,0)
        onView(withClassName(endsWith("EditText")))
            .perform(typeText(formText))
        InstrumentationUtility.exitForm(R.string.keep_changes)
        val time : String = (LocalTime.now()).format(DateTimeFormatter.ofPattern("h:mm:ss a"))
        Log.i("Time",time)
        onView(withText(startsWith("Incomplete")))
            .perform(click())
        onView(
            CustomMatchers.find(
                allOf(withId(R.id.formrecord_txt_btm)),
                1
            ))
            .check(matches(withSubstring("Form Summary: "+formText)))
        val recorderTime = InstrumentationUtility.getText(onView(
            CustomMatchers.find(
                allOf(withId(R.id.formrecord_txt_right)),
                1
            )))
        Log.i("Recorded time:", recorderTime)
        val differenceInSeconds = abs((LocalTime.parse(recorderTime, DateTimeFormatter.ofPattern("h:mm:ss a"))).second - (LocalTime.parse(time, DateTimeFormatter.ofPattern("h:mm:ss a"))).second)
        Log.i("Time Difference:", differenceInSeconds.toString())
        assertTrue(differenceInSeconds in (0.. 5000))
        InstrumentationUtility.clickListItem(R.id.screen_entity_select_list, 0)
        onView(withId(R.id.jumpBeginningButton))
            .perform(click())
        onView(withClassName(endsWith("EditText")))
            .check(matches(
                withText(formText)))
        InstrumentationUtility.submitForm()
        InstrumentationUtility.gotoHome()
    }

    @Test
    fun testIncompleteFormCaseList(){
        val formText = "Case List Test"
        InstrumentationUtility.openForm(3,0)
        onView(withClassName(endsWith("EditText")))
            .perform(typeText(formText))
        InstrumentationUtility.exitForm(R.string.keep_changes)
        val time : String = (LocalTime.now()).format(DateTimeFormatter.ofPattern("h:mm:ss a"))
        Log.i("Time",time)
        onView(withText(startsWith("Incomplete")))
            .perform(click())
        onView(
            CustomMatchers.find(
                allOf(withId(R.id.formrecord_txt_btm)),
                1
            ))
            .check(matches(withSubstring("Form Summary: "+formText)))
        val recorderTime = InstrumentationUtility.getText(onView(
            CustomMatchers.find(
                allOf(withId(R.id.formrecord_txt_right)),
                1
            )))
        Log.i("Recorded time:", recorderTime)
        val differenceInSeconds = abs((LocalTime.parse(recorderTime, DateTimeFormatter.ofPattern("h:mm:ss a"))).second - (LocalTime.parse(time, DateTimeFormatter.ofPattern("h:mm:ss a"))).second)
        Log.i("Time Difference:", differenceInSeconds.toString())
        assertTrue(differenceInSeconds in (0.. 5000))
        InstrumentationUtility.clickListItem(R.id.screen_entity_select_list, 0)
        onView(withId(R.id.jumpBeginningButton))
            .perform(click())
        onView(withClassName(endsWith("EditText")))
            .check(matches(
                withText(formText)))
        InstrumentationUtility.nextPage()
        onView(allOf(withClassName(endsWith("RadioButton")),
            withSubstring("Confirm"))).perform(click())
        InstrumentationUtility.submitForm()
    }




    private fun saveAndOpenIncompleteForm(){
        InstrumentationUtility.exitForm(R.string.keep_changes)
        InstrumentationUtility.openFirstIncompleteForm()
        onView(withId(R.id.jumpBeginningButton))
            .perform(click())
    }

    /**
     * function to set date in the date picker
     */
    fun setDateTo(days: Int) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DATE, +days)

        val year: Int = calendar.get(Calendar.YEAR)
        val month: Int = calendar.get(Calendar.MONTH) + 1
        val day: Int = calendar.get(Calendar.DAY_OF_MONTH)
        onView(
            ViewMatchers.withClassName(
                Matchers.equalTo(
                    DatePicker::class.java.name
                )
            )
        ).perform(PickerActions.setDate(year, month, day))
    }

    fun setTimeTo(hours: Int, mins: Int) {

        onView(withClassName(
            Matchers.equalTo(TimePicker::class.java.name)))
            .perform(PickerActions.setTime(hours, mins))
    }

}
