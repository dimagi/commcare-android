package org.commcare.androidTests


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
import org.junit.After
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

    @After
    fun teardown(){
        InstrumentationUtility.logout()
    }

    @Test
    fun testDateWidgets(){
        testDateWidgetMonths(listOfNepaliMonths, 0)
        testDateWidgetMonths(listOfEthiopianMonths, 1)
    }


    fun testDateWidgetMonths(listOfMonths: List<String>, module: Int){

        InstrumentationUtility.openModule(module)
        InstrumentationUtility.nextPage()
        InstrumentationUtility.nextPage()

            // reads the current month, gets the current index in the list, rotates the list starting from current month
        val monthText = InstrumentationUtility.getText(onView(withId(R.id.monthtxt)))
        listOfMonths.toMutableList()
        val index = listOfMonths.indexOf(monthText)
        assertTrue(index != -1)
        Collections.rotate(listOfMonths, -index)

            //asserts if months are present in order
        for (listItem in listOfMonths){
            assertTrue(onView(withText(listItem)).isPresent())
            onView(withId(R.id.monthupbtn)).perform(ViewActions.click())
        }

        onView(withId(R.id.yeardownbtn)).perform(ViewActions.click())
        val dateSelected = setDateToUniversalCalender(-10)
        val gregorianDate = InstrumentationUtility.getText(onView(withId(R.id.dateGregorian)))
        val formattedDate = formatGregorianDate(gregorianDate.drop(1).dropLast(1))
        InstrumentationUtility.nextPage()
        assertTrue(onView(withSubstring(formattedDate)).isPresent())
        assertTrue(onView(withSubstring(dateSelected)).isPresent())
        InstrumentationUtility.submitForm()
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())
    }

    @Test
    fun testStandardWidget(){
        InstrumentationUtility.openModule(4)
        InstrumentationUtility.rotateLeft()
        val date = setDateTo(-10)
        val dateFormates = getDateInDifferentFormats(date)
        InstrumentationUtility.rotatePortrait()
        InstrumentationUtility.nextPage()
        for (formats in dateFormates){
            assertTrue(onView(allOf(withSubstring(formats))).isPresent())
        }
        InstrumentationUtility.submitForm()
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())

    }

    /**
     * function to set date in the date picker
     */
    fun setDateTo(days: Int) : Date {
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

        return calendar.time
    }

    /**
     * function returns different format of the selected date
     */
    fun getDateInDifferentFormats(date : Date) : Array<String>{
        val longFormat = SimpleDateFormat("EE, MMM dd, yyyy").format(date)
        val shortFormat = SimpleDateFormat("d/M/yy").format(date)
        val unformattedDate = SimpleDateFormat("yyyy-MM-dd").format(date)
        return arrayOf(longFormat.toString(),shortFormat.toString(),unformattedDate.toString())
    }
    /**
     * function to set date in the date universal picker
     */
    fun setDateToUniversalCalender(days: Int) : String{

        var dayChangeViewInteraction = if(days > 0) onView(withId(R.id.dayupbtn)) else onView(withId(R.id. daydownbtn))
        for (day in 1..days.absoluteValue){
            dayChangeViewInteraction.perform(ViewActions.click())
        }
        var selectedDay = InstrumentationUtility.getText(onView(withId(R.id.daytxt)))
        val selectedMonth = InstrumentationUtility.getText(onView(withId(R.id.monthtxt)))
        val selectedYear = InstrumentationUtility.getText(onView(withId(R.id.yeartxt)))
        selectedDay = selectedDay.replaceFirst("0","")
        val selectedDate = selectedDay+" "+selectedMonth+" "+selectedYear
        return selectedDate
    }

    /**
     * format the gregorian date
     */
    fun formatGregorianDate(date: String) : String{
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
        val date = LocalDate.parse(date, formatter)
        val newDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(date)
        return newDate
    }
}