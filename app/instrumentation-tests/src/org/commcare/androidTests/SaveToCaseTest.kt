package org.commcare.androidTests


import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.annotations.BrowserstackTests
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
import org.hamcrest.Matchers
import org.commcare.dalvik.R
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class SaveToCaseTest: BaseTest() {
    companion object {
        const val CCZ_NAME = "case_managements_tests.ccz"
        const val APP_NAME = "Case Managements!"
        val valueMap = mapOf(
            "name" to randomStringGenerator(6),
            "number" to randomNumberGenerator(),
            "text" to "random text" + randomStringGenerator(5)
        )

        private fun randomStringGenerator(len: Int): String{
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            return (1..len)
                .map { allowedChars.random() }
                .joinToString("")
        }

        private fun randomNumberGenerator(): String {
            val number =  Random.nextInt()
            return number.toString()
        }


    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test1", "123")
        if (onView(withText("UPDATE TO THE LATEST APP VERSION")).isPresent()){
            onView(withText("UPDATE TO THE LATEST APP VERSION")).perform(click())
            InstrumentationUtility.gotoHome()
        }
    }

    @Test
    fun testSaveToCase(){
        createCase()
        saveToCaseUpdate()
    }

    fun createCase(){
        InstrumentationUtility.openForm(1,0)
        onView(withClassName(Matchers.endsWith("EditText")))
            .perform(typeText(valueMap["name"]))
        InstrumentationUtility.nextPage()
        onView(withClassName(Matchers.endsWith("EditText")))
            .perform(typeText(valueMap["number"]))
        InstrumentationUtility.nextPage()
        onView(withClassName(Matchers.endsWith("EditText")))
            .perform(typeText(valueMap["text"]))
        InstrumentationUtility.submitForm()
        InstrumentationUtility.waitForView(withText("1 form sent to server!"))
    }

    fun saveToCaseUpdate(){
        InstrumentationUtility.openForm(1,1)
        onView(withId(R.id.search_action_bar)).perform(click())
        onView(withId(R.id.search_src_text)).perform(typeText(valueMap["name"]))
        InstrumentationUtility.clickListItem(R.id.screen_entity_select_list,0)
        assertTrue(onView(withText(valueMap["name"])).isPresent())
        onView(withText("CONTINUE")).perform(click())
        onView(withClassName(Matchers.endsWith("EditText")))
            .check(matches(withText(valueMap["name"])))
        InstrumentationUtility.nextPage()
        onView(withClassName(Matchers.endsWith("EditText")))
            .check(matches(withText(valueMap["number"])))
        InstrumentationUtility.nextPage()
        onView(withClassName(Matchers.endsWith("EditText")))
            .check(matches(withText(valueMap["text"])))
        InstrumentationUtility.submitForm()
        InstrumentationUtility.waitForView(withText("1 form sent to server!"))
    }


}