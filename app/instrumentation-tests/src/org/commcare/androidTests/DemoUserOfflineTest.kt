package org.commcare.androidTests

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import org.commcare.utils.InstrumentationUtility
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
class DemoUserOfflineTest: DemoUserTest() {

    companion object {
        const val CCZ_NAME = "demo_user_test_1.ccz"
        const val APP_NAME = "Demo User Restore Test"
    }

    @Before
    fun setup() {
        InstrumentationUtility.changeWifi(false)
        installApp(APP_NAME, CCZ_NAME, true)
    }

    @Test
    fun testPracticeMode_online() {
        testPracticeMode()
    }

    @Test
    fun testPracticeMode_withUpdatedApp_online() {
        // Briefly turn the internet back on to allow us to log in
        InstrumentationUtility.changeWifi(true)
        updateApp()
        // Internet back off
        InstrumentationUtility.changeWifi(false)
        testPracticeMode_withUpdatedApp()
    }

}
