package org.commcare.androidTests



import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches

import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest

import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.InstrumentationUtility

import org.commcare.utils.isPresent
import org.hamcrest.Matchers

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class ConditionalEnumInCaseListTests: BaseTest() {
    companion object {
        const val CCZ_NAME = "conditional_enum_in_case_list.ccz"
        const val APP_NAME = "Conditional Enum in Case List Tests"
        val date = LocalDate.now().toString()
        val listOfCases = listOf(
            listOf("Test One "+date, "16", "Today"),
            listOf("Test Two "+date, "21", "Yesterday")
        )
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test1", "123")
    }

    @Test
    fun testCreateCases(){
        for (caseList in listOfCases) {
            onView(ViewMatchers.withText("Start"))
                .perform(ViewActions.click())
            onView(ViewMatchers.withText("Case List"))
                .perform(ViewActions.click())
            onView(ViewMatchers.withText("Register"))
                .perform(ViewActions.click())
            onView(ViewMatchers.withClassName(Matchers.endsWith("EditText")))
                .perform(ViewActions.typeText((caseList[0])))
            onView(ViewMatchers.withId(R.id.nav_btn_next))
                .perform(ViewActions.click())
            if (caseList[2] == "Yesterday") {
                InstrumentationUtility.setDateTo(1, "Past")
                Thread.sleep(1000)
                onView(ViewMatchers.withId(R.id.nav_btn_next))
                    .perform(ViewActions.click())
            } else if (caseList[2] == "Today") {
                onView(ViewMatchers.withId(R.id.nav_btn_next))
                    .perform(ViewActions.click())
            }

            onView(ViewMatchers.withClassName(Matchers.endsWith("EditText")))
                .perform(ViewActions.typeText((caseList[1])))
            onView(ViewMatchers.withId(R.id.nav_btn_finish))
                .perform(ViewActions.click())
            assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())
        }
    }

    @Test
    fun testUpdateCase1(){
        onView(ViewMatchers.withText("Start"))
            .perform(ViewActions.click())
        onView(ViewMatchers.withText("Case List"))
            .perform(ViewActions.click())
        onView(ViewMatchers.withText("Followup"))
            .perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.search_action_bar)).perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.search_src_text)).perform(ViewActions.typeText(listOfCases[0][0]))
        onView(ViewMatchers.withId(R.id.screen_entity_detail_list)).isPresent()
        InstrumentationUtility.clickListItem(R.id.screen_entity_select_list, 0)
        Thread.sleep(2000)
        assertTrue(InstrumentationUtility.verifyFormCellAndValue("Case Age","Child"))
        assertTrue(InstrumentationUtility.verifyFormCellAndValue("Case Age Even/Odd","Even"))
        if (listOfCases[0][2] == "Yesterday") {
            assertTrue(InstrumentationUtility.verifyFormCellAndValue("Appointment Date","Not Today"))
        } else if (listOfCases[0][2] == "Today") {
            assertTrue(InstrumentationUtility.verifyFormCellAndValue("Appointment Date","Today"))
        }

        onView(ViewMatchers.withText("CONTINUE")).perform(ViewActions.click())
        onView(ViewMatchers.withText("Did patient show up to their appointment?")).isPresent()
        onView(ViewMatchers.withText("yes")).perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.nav_btn_next))
            .perform(ViewActions.click())
        onView(ViewMatchers.withText("Checkup")).perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.nav_btn_finish))
            .perform(ViewActions.click())
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())
    }

    @Test
    fun testUpdateCase2(){
        onView(ViewMatchers.withText("Start"))
            .perform(ViewActions.click())
        onView(ViewMatchers.withText("Case List"))
            .perform(ViewActions.click())
        onView(ViewMatchers.withText("Followup"))
            .perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.search_action_bar)).perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.search_src_text)).perform(ViewActions.typeText(listOfCases[1][0]))
        onView(ViewMatchers.withId(R.id.screen_entity_detail_list)).isPresent()
        InstrumentationUtility.clickListItem(R.id.screen_entity_select_list, 0)
        Thread.sleep(2000)
        assertTrue(InstrumentationUtility.verifyFormCellAndValue("Case Age","Adult"))
        assertTrue(InstrumentationUtility.verifyFormCellAndValue("Case Age Even/Odd","Odd"))
        if (listOfCases[1][2] == "Yesterday") {
            assertTrue(InstrumentationUtility.verifyFormCellAndValue("Appointment Date","Not Today"))
        } else if (listOfCases[1][2] == "Today") {
            assertTrue(InstrumentationUtility.verifyFormCellAndValue("Appointment Date","Today"))
        }
        onView(ViewMatchers.withText("CONTINUE")).perform(ViewActions.click())
        onView(ViewMatchers.withText("Did patient show up to their appointment?")).isPresent()
        onView(ViewMatchers.withText("no")).perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.nav_btn_finish))
            .perform(ViewActions.click())
        assertTrue(onView(ViewMatchers.withText("1 form sent to server!")).isPresent())

    }

    @Test
    fun testVerifyChanges(){
        onView(ViewMatchers.withText("Start"))
            .perform(ViewActions.click())
        onView(ViewMatchers.withText("Case List"))
            .perform(ViewActions.click())
        onView(ViewMatchers.withText("Followup"))
            .perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.search_action_bar)).perform(ViewActions.click())
        onView(ViewMatchers.withId(R.id.search_src_text)).perform(ViewActions.typeText(listOfCases[0][0]))
        onView(ViewMatchers.withId(R.id.screen_entity_detail_list)).isPresent()
        InstrumentationUtility.clickListItem(R.id.screen_entity_select_list, 0)
        Thread.sleep(2000)
        assertTrue(InstrumentationUtility.verifyFormCellAndValue("Appointment Attended?","Seen by doctor"))
        InstrumentationUtility.hardPressBack()
        onView(ViewMatchers.withId(R.id.search_src_text)).perform(ViewActions.clearText())
        onView(ViewMatchers.withId(R.id.search_src_text)).perform(ViewActions.typeText(listOfCases[1][0]))
        onView(ViewMatchers.withId(R.id.screen_entity_detail_list)).isPresent()
        InstrumentationUtility.clickListItem(R.id.screen_entity_select_list, 0)
        Thread.sleep(2000)
        assertTrue(InstrumentationUtility.verifyFormCellAndValue("Appointment Attended?","Needs followup"))

    }

}