package org.commcare.androidTests



import android.widget.DatePicker
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.contrib.PickerActions

import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest

import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility

import org.commcare.utils.isPresent
import org.hamcrest.Matchers
import org.junit.After

import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.time.LocalDate
import java.util.*


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class ConditionalEnumInCaseListTests: BaseTest() {
    companion object {
        const val CCZ_NAME = "conditional_enum_in_case_list.ccz"
        const val APP_NAME = "Conditional Enum in Case List Tests"
        val date = LocalDate.now().toString()
        //the list contains the form input values of name, age, appointment date and appointment attended
        val listOfCases = listOf(
            listOf("Test One "+date, "16", "Today", "yes"),
            listOf("Test Two "+date, "21", "Yesterday", "no")
        )
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
    fun testCreateCases(){
        for (caseList in listOfCases) {
            InstrumentationUtility.openForm(0, 0)
            InstrumentationUtility.enterText(caseList[0])
            InstrumentationUtility.nextPage()
            if (caseList[2] == "Yesterday") {
                setDateTo(-1)
                Thread.sleep(1000) // without the wait the test fails with AmbiguousViewMatcherException and does not click the next button

            } else if (caseList[2] == "Today") {
                setDateTo(0)
            }
            InstrumentationUtility.nextPage()
            InstrumentationUtility.enterText(caseList[1])
            InstrumentationUtility.submitForm()
            assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())
        }
        // code to update the cases
        for (caseList in listOfCases) {
            InstrumentationUtility.openForm(0,1)
            InstrumentationUtility.searchCaseAndSelect(caseList[0])
            Thread.sleep(2000)
            if (caseList[1].toInt() < 20){
                InstrumentationUtility.verifyFormCellAndValue("Case Age","Child")
            }else{
                InstrumentationUtility.verifyFormCellAndValue("Case Age","Adult")
            }
            if (caseList[1].toInt() % 2 == 0) {
                InstrumentationUtility.verifyFormCellAndValue("Case Age Even/Odd", "Even")
            }else {
                InstrumentationUtility.verifyFormCellAndValue("Case Age Even/Odd", "Odd")
            }
            if (caseList[2] == "Yesterday") {
                InstrumentationUtility.verifyFormCellAndValue("Appointment Date","Not Today")
            } else if (caseList[2] == "Today") {
                InstrumentationUtility.verifyFormCellAndValue("Appointment Date","Today")
            }
            onView(ViewMatchers.withText("CONTINUE")).perform(ViewActions.click())
            onView(ViewMatchers.withText("Did patient show up to their appointment?")).isPresent()
            if (caseList[3] == "yes") {
                onView(ViewMatchers.withText("yes")).perform(ViewActions.click())
                InstrumentationUtility.nextPage()
                onView(ViewMatchers.withText("Checkup")).perform(ViewActions.click())
            }else {
                onView(ViewMatchers.withText("no")).perform(ViewActions.click())
            }
            InstrumentationUtility.submitForm()
            assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())
        }
            //code to verify the updates made
        for (caseList in listOfCases) {
            InstrumentationUtility.openForm(0,1)
            InstrumentationUtility.searchCaseAndSelect(caseList[0])
            if (caseList[3] == "yes") {
                InstrumentationUtility.verifyFormCellAndValue("Appointment Attended?","Seen by doctor")
            }else {
                InstrumentationUtility.verifyFormCellAndValue("Appointment Attended?","Needs followup")
            }
            InstrumentationUtility.gotoHome()
        }

    }

    /**
     * function to set date in the date picker
     */
    fun setDateTo(days: Int){
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

}