package org.commcare.androidTests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.annotations.BrowserstackTests
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class DemoUserOnlineTest: DemoUserTest() {

    companion object {
        const val CCZ_NAME = "demo_user_test_1.ccz"
        const val APP_NAME = "Demo User Restore Test"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME, true)
    }

    @Test
    fun testPracticeMode_online() {
        testPracticeMode()
    }

    @Test
    fun testPracticeMode_withUpdatedApp_online() {
        updateApp()
        testPracticeMode_withUpdatedApp()
    }
}
