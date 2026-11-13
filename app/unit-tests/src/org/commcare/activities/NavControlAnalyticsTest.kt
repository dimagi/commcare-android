package org.commcare.activities

import android.os.Build
import android.view.KeyEvent
import android.view.MenuItem
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class NavControlAnalyticsTest {
    private lateinit var activity: NavControlTestActivity

    @Before
    fun setUp() {
        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportNavControlUsed(any(), any()) } just runs
        activity = Robolectric.buildActivity(NavControlTestActivity::class.java).setup().get()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `the app-bar arrow is reported as the app-bar back button`() {
        activity.onOptionsItemSelected(mockk<MenuItem> { every { itemId } returns android.R.id.home })

        verify(exactly = 1) {
            FirebaseAnalyticsUtil.reportNavControlUsed(
                AnalyticsParamValue.APP_BAR_BACK_BUTTON_PRESS,
                NavControlTestActivity::class.java.simpleName,
            )
        }
    }

    @Test
    fun `the system back key is reported as the system back button`() {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        verify(exactly = 1) {
            FirebaseAnalyticsUtil.reportNavControlUsed(
                AnalyticsParamValue.SYSTEM_NAV_BACK_BUTTON_PRESS,
                NavControlTestActivity::class.java.simpleName,
            )
        }
    }

    @Config(sdk = [Build.VERSION_CODES.R])
    @Test
    fun `the system back key on a gesture-navigation device is reported as the back gesture`() {
        mockkStatic(ViewCompat::class)
        every { ViewCompat.getRootWindowInsets(any()) } returns buildGestureNavigationInsets()

        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))

        verify(exactly = 1) {
            FirebaseAnalyticsUtil.reportNavControlUsed(
                AnalyticsParamValue.SYSTEM_NAV_BACK_GESTURE,
                NavControlTestActivity::class.java.simpleName,
            )
        }
    }

    @Test
    fun `holding the system back key is reported once`() {
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        activity.dispatchKeyEvent(downEvent)
        activity.dispatchKeyEvent(KeyEvent.changeTimeRepeat(downEvent, downEvent.eventTime, 1))

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportNavControlUsed(any(), any()) }
    }

    private fun buildGestureNavigationInsets(): WindowInsetsCompat =
        WindowInsetsCompat
            .Builder()
            .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(60, 0, 60, 48))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
            .build()

    class NavControlTestActivity : CommCareActivity<NavControlTestActivity>()
}
