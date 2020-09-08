package org.commcare.screenshotTests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.testing.screenshot.Screenshot
import org.commcare.CommCareInstrumentationTestApplication

import org.commcare.androidTests.BaseTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LoginActivityScreenshot: BaseTest() {

    @Test
    fun testLoginActivityUI() {
        installApp("TestSavedForm", "testSavedForm.ccz")
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as CommCareInstrumentationTestApplication
        Screenshot.snapActivity(application.currentActivity).record()
    }
}