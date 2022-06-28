package org.commcare.androidTests

import android.util.Log
import android.widget.DatePicker
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions

import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest

import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
import org.hamcrest.Matchers
import org.hamcrest.Matchers.allOf

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.absoluteValue



@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class DateWidgetsTests: BaseTest() {
    companion object {
        const val CCZ_NAME = "date_widgets_tests.ccz"
        const val APP_NAME = "Date Widgets"
        val listOfNepaliMonths = listOf("Baishakh","Jestha","Ashadh","Shrawan",
                            "Bhadra","Ashwin","Kartik","Mangsir","Poush","Magh",
                            "Falgun","Chaitra")
        val listOfEthiopianMonths = listOf("Säne","Hämle","Nähäse","P’agume",
            "Mäskäräm","T’ïk’ïmt","Hïdar","Tahsas","T’ïr","Yäkatit",
            "Mägabit","Miyaziya")

    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test1", "123")
    }

    /**
     * Adding the teardown method so the tests doesnot fail after only thefirst execution, when executed as a whole.
     */

    @Test
    fun testNepalDateWidgets(){

        InstrumentationUtility.openModule(0)
        onView(withSubstring("This form will test the Nepal date widget.")).isPresent()
        InstrumentationUtility.nextPage()
        InstrumentationUtility.nextPage()

        for (i in 1..12){
            val month_text = InstrumentationUtility.getText(onView(withId(R.id.monthtxt)))
            if (month_text != null) {
                assertTrue(listOfNepaliMonths.contains(month_text))
            }
            onView(withId(R.id.monthupbtn)).perform(ViewActions.click())
        }
        onView(withId(R.id.yeardownbtn)).perform(ViewActions.click())
        val date_selected = setDateToUniversalCalender(-10,0,0)
        val gregorian_date = InstrumentationUtility.getText(onView(withId(R.id.dateGregorian)))
        val formatted_date = formatGregorianDate(gregorian_date.drop(1).dropLast(1))

        InstrumentationUtility.nextPage()
        Log.i("Nepali date:", date_selected)
        Log.i("Gregorian date:", formatted_date)
        assertTrue(onView(withSubstring(formatted_date)).isPresent())
        assertTrue(onView(withSubstring(date_selected)).isPresent())
        InstrumentationUtility.submitForm()
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())
    }


    @Test
    fun testEthiopianDateWidgets(){

        InstrumentationUtility.openModule(1)
        onView(withSubstring("This form will test the Ethiopian date widget.")).isPresent()
        InstrumentationUtility.nextPage()
        InstrumentationUtility.nextPage()
        for (i in 1..12){
            val month_text = InstrumentationUtility.getText(onView(withId(R.id.monthtxt)))
            if (month_text != null) {
                assertTrue(listOfEthiopianMonths.contains(month_text))
            }
            onView(withId(R.id.monthupbtn)).perform(ViewActions.click())
        }

        onView(withId(R.id.yeardownbtn)).perform(ViewActions.click())
        val date_selected = setDateToUniversalCalender(-10,0,0)
        val gregorian_date = InstrumentationUtility.getText(onView(withId(R.id.dateGregorian)))
        val formatted_date = formatGregorianDate(gregorian_date.drop(1).dropLast(1))

        InstrumentationUtility.nextPage()
        Log.i("Ethiopian date:", date_selected)
        Log.i("Gregorian date:", formatted_date)
        assertTrue(onView(withSubstring(formatted_date)).isPresent())
        assertTrue(onView(withSubstring(date_selected)).isPresent())
        InstrumentationUtility.submitForm()
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())
    }

    @Test
    fun testStandardWidget(){

        InstrumentationUtility.openModule(4)
        InstrumentationUtility.rotateLeft()
        val dateList = setDateTo(-10)
//        Thread.sleep(1000)
        InstrumentationUtility.rotatePortrait()
        InstrumentationUtility.nextPage()

        for (formats in dateList){
            assertTrue(onView(allOf(withSubstring(formats))).isPresent())
        }

        InstrumentationUtility.submitForm()
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())
    }

    /**
     * function to set date in the date picker and return different format of the selected date
     */
    fun setDateTo(days: Int): Array<String> {
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

        val long_format = SimpleDateFormat("EE, MMM dd, yyyy").format(calendar.time)
        val short_format = SimpleDateFormat("d/M/yy").format(calendar.time)
        val unformatted_date = SimpleDateFormat("yyyy-MM-dd").format(calendar.time)
        return arrayOf(long_format.toString(),short_format.toString(),unformatted_date.toString())
    }

    /**
     * function to set date in the date universal picker
     */
    fun setDateToUniversalCalender(days: Int, months: Int, years: Int) : String{

        for (day in 1..days.absoluteValue){
            if (days > 0) {
                onView(withId(R.id.dayupbtn)).perform(ViewActions.click())
            }else if (days < 0){
                onView(withId(R.id.daydownbtn)).perform(ViewActions.click())
            }
        }

        for (month in 1..months.absoluteValue){
            if (months > 0) {
                onView(withId(R.id.monthupbtn)).perform(ViewActions.click())
            }else if (months < 0){
                onView(withId(R.id.monthdownbtn)).perform(ViewActions.click())
            }
        }

        for (year in 1..years.absoluteValue){
            if (years > 0) {
                onView(withId(R.id.yearupbtn)).perform(ViewActions.click())
            }else if (years < 0){
                onView(withId(R.id.yeardownbtn)).perform(ViewActions.click())
            }
        }
        var selectedDay = InstrumentationUtility.getText(onView(withId(R.id.daytxt)))
        val selectedMonth = InstrumentationUtility.getText(onView(withId(R.id.monthtxt)))
        val selectedYear = InstrumentationUtility.getText(onView(withId(R.id.yeartxt)))
        selectedDay = selectedDay.replaceFirst("0","")
        val selected_date = selectedDay+" "+selectedMonth+" "+selectedYear
        return selected_date
    }

    /**
     * format the gregorian date
     */
    fun formatGregorianDate(date: String) : String{
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
        val date = LocalDate.parse(date, formatter)
        val newdate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(date)
        return newdate
    }
}



