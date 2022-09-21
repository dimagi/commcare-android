package org.commcare.androidTests


import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import junit.framework.Assert.assertTrue
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.Matchers.*
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.util.*


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class OtherActionTests: BaseTest() {
    companion object {
        const val CCZ_NAME = "other.ccz"
        const val APP_NAME = "Other Test"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test1", "123")
    }

    @Test
    fun testActionKey() {
        InstrumentationUtility.openForm(0, 3)
        onView(withClassName(endsWith("EditText")))
            .perform(click())
        assertTrue(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_ENTER))
        onView(withClassName(endsWith("EditText")))
            .perform(typeText("Test for"),
                pressKey(KeyEvent.KEYCODE_ENTER),
                typeTextIntoFocusedView("Enter Key"))
        InstrumentationUtility.nextPage()
        onView(withClassName(endsWith("EditText"))).
        perform(typeText("123"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_DONE))
        onView(withClassName(endsWith("EditText"))).
        perform(pressKey(EditorInfo.IME_ACTION_DONE))
        InstrumentationUtility.nextPage()
        var first = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            ))
        first.perform(typeText("Test 1 for"),
            pressKey(KeyEvent.KEYCODE_ENTER),
            typeTextIntoFocusedView("Enter Key"))
        assertTrue(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_ENTER))
        onView(isRoot()).perform(closeSoftKeyboard())
        var second = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            ))
        second.perform(scrollTo(),typeText("Test 2 for"),
            pressKey(KeyEvent.KEYCODE_ENTER),
            typeTextIntoFocusedView("Enter Key"))
        assertTrue(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_ENTER))
        onView(isRoot()).perform(closeSoftKeyboard())
        var third = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
        third.perform(scrollTo(), typeText("Test 3 for"),
            pressKey(KeyEvent.KEYCODE_ENTER),
            typeTextIntoFocusedView("Enter Key"))
        assertTrue(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_ENTER))
        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            )).perform(typeText("1"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            )).perform(typeText("2"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())
        third = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
        third.perform(typeText("3"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_DONE))
        third.perform(pressKey(EditorInfo.IME_ACTION_DONE))

        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            )).perform(typeText("1.1"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            )).perform(typeText("2.1"))

        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())
        third = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
        third.perform(typeText("3.1"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_DONE))
        third.perform(pressKey(EditorInfo.IME_ACTION_DONE))

        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            )).perform(typeText("Test 1 for"),
            pressKey(KeyEvent.KEYCODE_ENTER),
            typeTextIntoFocusedView("Enter Key"))
        assertTrue(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_ENTER))
        onView(isRoot()).perform(closeSoftKeyboard())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            )).perform(scrollTo(),
            typeText("Test 2 for"),
            pressKey(KeyEvent.KEYCODE_ENTER),
            typeTextIntoFocusedView("Enter Key"))
        assertTrue(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_ENTER))
        onView(isRoot()).perform(closeSoftKeyboard())
        third = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
        third.perform(scrollTo(), typeText("3"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_DONE))
        third.perform(pressKey(EditorInfo.IME_ACTION_DONE))

        InstrumentationUtility.nextPage()
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            )).perform(typeText("1"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            )).perform(scrollTo(), typeText("2"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())
        onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            )).perform(scrollTo(),
            typeText("Test for"),
            pressKey(KeyEvent.KEYCODE_ENTER),
            typeTextIntoFocusedView("Enter Key"))
        assertTrue(KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_ENTER))
        onView(isRoot()).perform(closeSoftKeyboard())

        var fourth = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                4
            ))
        fourth.perform(scrollTo(), typeText("4"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_DONE))
        fourth.perform(pressKey(EditorInfo.IME_ACTION_DONE))

        InstrumentationUtility.nextPage()
        onView(withText("A")).perform(click())

        first = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
            ))
        first.perform(scrollTo(), typeText("1"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())

        second = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
            ))
        second.perform(scrollTo(), typeText("2"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())

        third = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                3
            ))
        third.perform(scrollTo(), typeText("3"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_DONE))
        third.perform(pressKey(EditorInfo.IME_ACTION_DONE))

        onView(withText("CLEAR")).perform(scrollTo(), click())
        onView(withText("B")).perform(click())

        first.perform(scrollTo(), clearText(), typeText("1"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())

        second.perform(scrollTo(), clearText(), typeText("2"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())

        third.perform(scrollTo(), clearText(),typeText("3"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_NEXT))
        onView(isRoot()).perform(closeSoftKeyboard())

        fourth = onView(
            CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                4
            ))
        fourth.perform(scrollTo(), typeText("4"))
        assertTrue(KeyCharacterMap.deviceHasKey(EditorInfo.IME_ACTION_DONE))
        fourth.perform(pressKey(EditorInfo.IME_ACTION_DONE))

        InstrumentationUtility.submitForm()
    }


}