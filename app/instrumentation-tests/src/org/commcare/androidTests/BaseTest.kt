package org.commcare.androidTests

import android.Manifest
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.concurrent.IdlingThreadPoolExecutor
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.asCoroutineDispatcher
import org.commcare.CommCareApplication
import org.commcare.activities.DispatchActivity
import org.commcare.tasks.templates.CoroutinesAsyncTask
import org.commcare.utils.InstrumentationUtility
import org.junit.Rule
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@LargeTest
abstract class BaseTest {

    @Rule
    @JvmField
    var intentsRule = IntentsTestRule(DispatchActivity::class.java)

    @Rule
    @JvmField
    var permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    )

    protected open fun installApp(appName: String, ccz: String, force: Boolean = false) {
        val executor = IdlingThreadPoolExecutor("testDispatcher",
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().availableProcessors(),
                0L,
                TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(),
                Executors.defaultThreadFactory())
        val dispatcher = executor.asCoroutineDispatcher()

        mockkObject(CoroutinesAsyncTask)
        every { CoroutinesAsyncTask.parallelDispatcher() } returns dispatcher
        every { CoroutinesAsyncTask.serialDispatcher() } returns dispatcher

        IdlingRegistry.getInstance().register(executor)
        IdlingRegistry.getInstance().register(executor)

        if (CommCareApplication.instance().currentApp == null) {
            InstrumentationUtility.installApp(ccz)
        } else if (appName != CommCareApplication.instance().currentApp.appRecord.displayName || force) {
            // We already have an installed app, But not the one we need for this test.
            InstrumentationUtility.uninstallCurrentApp()
            InstrumentationUtility.installApp(ccz)
            // App installation doesn't take back to login screen. Is this an issue?
            Espresso.pressBack()
        }
    }
}
