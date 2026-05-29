package org.commcare.androidTests


import androidx.test.espresso.Espresso.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.commcare.AppUtils
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.test.BuildConfig
import org.commcare.utils.InstrumentationUtility
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class SessionExpirationTests: BaseTest() {
    companion object {
        const val CCZ_NAME = "session_expiration_test.ccz"
        const val APP_NAME = "Session Expiration Test"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test_session_1", "123")
    }

    @Test
    fun testRestoreUser(){
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.openNotification()
        if (BuildConfig.DEBUG || AppUtils.getInstalledAppRecords().size <= 1) {
            uiDevice.wait(Until.hasObject(By.textStartsWith("Logged Into Commcare")),5000)
        } else {
            uiDevice.wait(Until.hasObject(By.textStartsWith("Logged Into $APP_NAME")),5000)
        }
        uiDevice.findObject(By.textStartsWith("Session Expires:")).click()
        uiDevice.wait(Until.hasObject(By.textStartsWith("Welcome")),45000)

        //after the user is logged out, verifies the login expired notification
        uiDevice.openNotification()
        uiDevice.wait(Until.hasObject(By.textEndsWith("Login Expire")), 1000)
    }

}
