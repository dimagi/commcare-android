package org.commcare.utils

import android.os.Build
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.R])
@RunWith(AndroidJUnit4::class)
class SystemNavigationModeTest {
    @Test
    fun `back-gesture areas on the side edges mean gesture navigation`() {
        val insets = buildInsets(gestures = Insets.of(60, 0, 60, 48), navigationBar = Insets.of(0, 0, 0, 48))

        assertTrue(SystemNavigationMode.isGestureNavigation(insets))
    }

    @Test
    fun `a bottom 3-button bar is not gesture navigation`() {
        val insets = buildInsets(gestures = Insets.of(0, 0, 0, 126), navigationBar = Insets.of(0, 0, 0, 126))

        assertFalse(SystemNavigationMode.isGestureNavigation(insets))
    }

    @Test
    fun `a 3-button bar on the side in landscape is not gesture navigation`() {
        val insets = buildInsets(gestures = Insets.of(0, 0, 126, 0), navigationBar = Insets.of(0, 0, 126, 0))

        assertFalse(SystemNavigationMode.isGestureNavigation(insets))
    }

    @Test
    fun `missing insets are not gesture navigation`() {
        assertFalse(SystemNavigationMode.isGestureNavigation(null))
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `devices below Android 10 are never gesture navigation`() {
        val insets = buildInsets(gestures = Insets.of(60, 0, 60, 48), navigationBar = Insets.of(0, 0, 0, 48))

        assertFalse(SystemNavigationMode.isGestureNavigation(insets))
    }

    private fun buildInsets(
        gestures: Insets,
        navigationBar: Insets,
    ): WindowInsetsCompat =
        WindowInsetsCompat
            .Builder()
            .setInsets(WindowInsetsCompat.Type.systemGestures(), gestures)
            .setInsets(WindowInsetsCompat.Type.navigationBars(), navigationBar)
            .build()
}
