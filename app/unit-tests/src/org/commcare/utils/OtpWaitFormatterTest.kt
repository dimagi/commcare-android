package org.commcare.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class OtpWaitFormatterTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `a sub-minute wait is reported in seconds`() {
        assertEquals(getExpectedWaitText(R.plurals.personalid_otp_retry_after_seconds, 30), getFormattedWaitText(30))
    }

    @Test
    fun `a wait of a minute or more is reported in minutes`() {
        assertEquals(getExpectedWaitText(R.plurals.personalid_otp_retry_after_minutes, 4), getFormattedWaitText(240))
    }

    @Test
    fun `a part-minute wait rounds up`() {
        assertEquals(getExpectedWaitText(R.plurals.personalid_otp_retry_after_minutes, 3), getFormattedWaitText(121))
    }

    @Test
    fun `a wait of an hour or more is reported in hours`() {
        assertEquals(getExpectedWaitText(R.plurals.personalid_otp_retry_after_hours, 4), getFormattedWaitText(4 * 60 * 60))
    }

    @Test
    fun `a part-hour wait rounds up`() {
        assertEquals(getExpectedWaitText(R.plurals.personalid_otp_retry_after_hours, 2), getFormattedWaitText(61 * 60))
    }

    @Test
    fun `just under an hour is still reported in minutes`() {
        assertEquals(getExpectedWaitText(R.plurals.personalid_otp_retry_after_minutes, 59), getFormattedWaitText(59 * 60))
    }

    @Test
    fun `a wait that has all but elapsed still reads as a wait`() {
        assertEquals(getExpectedWaitText(R.plurals.personalid_otp_retry_after_seconds, 1), getFormattedWaitText(0))
    }

    @Test
    fun `the singular form is used for a wait of one`() {
        assertEquals(getExpectedWaitText(R.plurals.personalid_otp_retry_after_minutes, 1), getFormattedWaitText(60))
        assertEquals(getExpectedWaitText(R.plurals.personalid_otp_retry_after_hours, 1), getFormattedWaitText(60 * 60))
    }

    private fun getFormattedWaitText(waitSeconds: Int) = OtpWaitFormatter.format(context, waitSeconds)

    private fun getExpectedWaitText(
        pluralsResId: Int,
        quantity: Int,
    ) = context.resources.getQuantityString(pluralsResId, quantity, quantity)
}
