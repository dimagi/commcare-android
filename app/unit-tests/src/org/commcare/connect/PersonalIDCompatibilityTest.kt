package org.commcare.connect

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIDCompatibilityTest {
    private val manager = PersonalIdManager.getInstance()

    @Test
    @Config(sdk = [Build.VERSION_CODES.P], qualifiers = "sw360dp")
    fun `phone width below 600dp is compatible on API 28+`() {
        assertTrue(manager.checkDeviceCompability())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P], qualifiers = "sw599dp")
    fun `width at 599dp is compatible on API 28+`() {
        assertTrue(manager.checkDeviceCompability())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P], qualifiers = "sw600dp")
    fun `width at exactly 600dp is not compatible`() {
        assertFalse(manager.checkDeviceCompability())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P], qualifiers = "sw720dp")
    fun `tablet width above 600dp is not compatible`() {
        assertFalse(manager.checkDeviceCompability())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O], qualifiers = "sw360dp")
    fun `API below 28 is not compatible regardless of screen width`() {
        assertFalse(manager.checkDeviceCompability())
    }
}
