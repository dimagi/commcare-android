package org.commcare.androidTests


import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice

import androidx.test.uiautomator.Until
import org.commcare.annotations.BrowserstackTests
import org.commcare.utils.InstrumentationUtility

import org.junit.Assert

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class SessionExpirationTests: BaseTest() {
    companion object {
        const val CCZ_NAME = "integration_test_app.ccz"
        const val APP_NAME = "Integration Tests"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test_user_13", "123")
    }

    @Test
    fun testRestoreUser(){
        onView(withText("Start"))
            .perform(ViewActions.click())
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.openNotification()
        uiDevice.wait(Until.hasObject(By.textStartsWith("Logged Into")), 45000)
        val text = uiDevice.findObject(By.textStartsWith("Session")).text
        uiDevice.findObject(By.textStartsWith("Session")).click()
        Assert.assertTrue( text.contains("Session Expires:"))
        InstrumentationUtility.logout()
    }

}
