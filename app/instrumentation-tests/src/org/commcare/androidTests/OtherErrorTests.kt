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
class OtherErrorTests: BaseTest() {
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
    fun testXformErrors(){
        InstrumentationUtility.openForm(1,0)
        testImmediateFail()
        testDelayedFail()
        testRelevancyFail()
        testRepeatError()
    }

    fun testImmediateFail(){
        assertTrue(onView(withSubstring("Error in calculation for /data/immediate_fail")).isPresent())
        onView(withText("OK")).perform(click())
        assertTrue(onView(withText("XForm Error Tests")).isPresent())
    }
    fun testDelayedFail(){
        onView(withText("Delayed Fail")).perform(click())
        onView(withText("Item 1")).perform(click())
        assertTrue(onView(withSubstring("Error in calculation for /data/calculated_value")).isPresent())
        onView(withText("OK")).perform(click())
        assertTrue(onView(withText("XForm Error Tests")).isPresent())
    }
    fun testRelevancyFail(){
        onView(withText("Relevancy Fail")).perform(click())
        assertTrue(onView(withSubstring("Error in calculation for /data/bad_relevancy")).isPresent())
        onView(withText("OK")).perform(click())
        assertTrue(onView(withText("XForm Error Tests")).isPresent())
    }
    fun testRepeatError(){
        onView(withText("Repeat Error")).perform(click())
        assertTrue(onView(withSubstring("Error in calculation for /data/hidden_value")).isPresent())
        onView(withText("OK")).perform(click())
        assertTrue(onView(withText("XForm Error Tests")).isPresent())
    }
}