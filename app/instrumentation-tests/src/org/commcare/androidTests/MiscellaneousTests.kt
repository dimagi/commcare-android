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
class MiscellaneousTests : BaseTest() {

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
    fun testQuestionTypes() {
        InstrumentationUtility.openForm(0,1)
        onView(withText("OK. Please continue.")).perform(click())
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("Try!&g different v@lues# 123"))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("123"))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("11.0"))
        InstrumentationUtility.nextPage()
        onView(withId(R.id.nav_btn_prev))
            .perform(click())
        onView(withClassName(endsWith("EditText"))).check(matches(withText("11")))
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
    fun testTimeDuration(){
        InstrumentationUtility.openForm(0,11)
        var startTime : Long = System.nanoTime()
        InstrumentationUtility.waitForView(withSubstring("This form contains a 1MB ficture"))
        var stopTime : Long = System.nanoTime()
        var total : Long = TimeUnit.NANOSECONDS.toMillis(stopTime - startTime)
        Log.i("Total Time for load", total.toString())
        InstrumentationUtility.nextPage()
        onView(CustomMatchers.find(
            allOf(withClassName(endsWith("RadioButton"))),
            1
        )).perform(click())
        InstrumentationUtility.submitForm()
        startTime = System.nanoTime()
        InstrumentationUtility.waitForView(allOf(withId(R.id.home_card), withText("Start")))
        stopTime = System.nanoTime()
        total= TimeUnit.NANOSECONDS.toMillis(stopTime - startTime)
        Log.i("Total Time for submission", total.toString())
    }

    @Test
    fun testRepeats(){
        InstrumentationUtility.openForm(0,2)
        val initialProgress = getProgress()
        InstrumentationUtility.nextPage()
        onView(withText("ADD GROUP")).perform(click())
        onView(withClassName(endsWith("EditText"))).perform(typeText("Group 1"))
        InstrumentationUtility.nextPage()
        onView(withText("Two")).perform(click())
        InstrumentationUtility.nextPage()
        onView(withText("ADD GROUP")).perform(click())
        onView(withClassName(endsWith("EditText"))).perform(typeText("Group 2"))
        InstrumentationUtility.nextPage()
        onView(withText("One")).perform(click())
        val currentProgress = getProgress()
        Log.i("Initial Progress", initialProgress.toString())
        Log.i("Current Progress", currentProgress.toString())
        InstrumentationUtility.nextPage()
        onView(withText("DO NOT ADD")).perform(click())
        var newProgress = getProgress()
        Log.i("New Progress", newProgress.toString())
        onView(withText("OK. Please continue.")).perform(click())
        InstrumentationUtility.nextPage()
        onView(withText("DO NOT ADD")).perform(click())
        newProgress = getProgress()
        Log.i("New Progress", newProgress.toString())
        assertTrue(newProgress > currentProgress)
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("3"))
                Thread.sleep(2000)
        onView(withClassName(endsWith("EditText")))
            .perform(clearText(),typeText("1"))

        InstrumentationUtility.nextPage()
        val progressAfter= getProgress()
        onView(withId(R.id.nav_btn_prev)).perform(click())
        val progressBefore= getProgress()
        assertTrue(progressAfter > progressBefore)
        InstrumentationUtility.nextPage()
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("Group 3"))
        InstrumentationUtility.nextPage()
        InstrumentationUtility.nextPage()
        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Suffolk"))).check(matches(isChecked()))
        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Middlesex"))).check(matches(isChecked()))
        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Essex"))).check(matches(isChecked()))
        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Middlesex"))).perform(click())
        onView(allOf(withClassName(endsWith("CheckBox")),
            withText("Middlesex"))).check(matches(isNotChecked()))
        InstrumentationUtility.nextPage()
        assertTrue(onView(
            withText("Cities you can visit in Suffolk")
        ).isPresent())
        assertTrue(onView(
            withText("Cities you can visit in Essex")
        ).isPresent())
        onView(withText("FINISH"))
            .check(matches(CustomMatchers.withTextColor(Color.WHITE)))

        onView(withId(R.id.nav_btn_finish))
            .check(matches(isDisplayed()))
        InstrumentationUtility.submitForm()
        InstrumentationUtility.waitForView(ViewMatchers.withText("1 form sent to server!"))
        InstrumentationUtility.gotoHome()
    }

    @Test
    fun testLocations(){
        InstrumentationUtility.openForm(0,9)
        InstrumentationUtility.nextPage()
        Thread.sleep(1000)
        Espresso.closeSoftKeyboard()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            )).check(matches(
                withText("Falmouth")
            ))

        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            ))
            .check(matches(
                withText("84299aee3f3e46bd92b26677762ad3c5")
            ))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
            .check(matches(
                withText("outlet")
            ))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                4
            ))
            .check(matches(
                withText("outlet01")
            ))
        InstrumentationUtility.nextPage()
        Thread.sleep(1000)
        Espresso.closeSoftKeyboard()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            ))
            .check(matches(
                withText("Barnstable Country")
            ))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
               2
            ))
            .check(matches(
                withText("e5d5c2d0d56c42b7b3a1f48cd198b960")
            ))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
            .check(matches(
                withText("barnstable")
            ))
        InstrumentationUtility.nextPage()
        Thread.sleep(1000)
        Espresso.closeSoftKeyboard()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            ))
            .check(matches(
                withText("Cape Cod")
            ))


        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            ))
            .check(matches(
                withText("7b13fb20198548f992c0397443d74a29")
            ))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
            .check(matches(
                withText("cape_cod")
            ))
        InstrumentationUtility.nextPage()
        Thread.sleep(1000)
        Espresso.closeSoftKeyboard()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            ))
            .check(matches(
                withText("Massachusetts")
            ))

        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            ))
            .check(matches(
                withText("de9011090082415faaab75e40fcf1946")
            ))
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
            .check(matches(
                withText("massachusetts")
            ))
        InstrumentationUtility.nextPage()
        onView(
            allOf(
                withClassName(endsWith("RadioButton")),
                withText("Falmouth")

            )
        ).perform(click())
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText")))
            .check(matches(
                withText("Medium")
            ))
        InstrumentationUtility.submitForm()
        InstrumentationUtility.waitForView(ViewMatchers.withText("1 form sent to server!"))
    }

    @Test
    fun testCaseName(){
        val longName = getRandomString(256)
        Log.i("Long name", longName)
        InstrumentationUtility.openForm(3,0)
        onView(withClassName(endsWith("EditText")))
            .perform(typeText(longName))
        InstrumentationUtility.nextPage()
        onView(allOf(withClassName(endsWith("RadioButton")),
        withSubstring("Confirm"))).perform(click())
        InstrumentationUtility.submitForm()
        InstrumentationUtility.waitForView(
            withText("Error Saving your Form"),
        20000)
        assertTrue(
        onView(withText("Invalid case_name, value must be 255 characters or less"))
            .isPresent())
        onView(withText("OK")).perform(click())
        InstrumentationUtility.gotoHome()
        onView(withText("Saved")).perform(click())
        assertFalse(onView(withText("Create a Case"))
            .isPresent())
        InstrumentationUtility.gotoHome()
    }

    /**
     * function to get progress of the Progress bar
     */
    private fun getProgress() : Int {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as CommCareInstrumentationTestApplication
        val progressBar = application.currentActivity.findViewById<ProgressBar>(R.id.nav_prog_bar)
        return progressBar.progress
    }

    /**
     * function to set date in the date picker
     */
    private fun setDateTo(days: Int) {
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

    /**
     * function to set time in the time picker
     */
    fun setTimeTo(hours: Int, mins: Int) {

        onView(withClassName(
            Matchers.equalTo(TimePicker::class.java.name)))
            .perform(PickerActions.setTime(hours, mins))
    }

    /**
     * function to generate random string
     */
    private fun getRandomString(length: Int) : String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
