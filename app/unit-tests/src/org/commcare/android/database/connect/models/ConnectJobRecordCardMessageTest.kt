package org.commcare.android.database.connect.models

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.commcare.CommCareTestApplication
import org.commcare.connect.database.ConnectTaskUtils
import org.commcare.dalvik.R
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Covers the visit-limit branches of [ConnectJobRecord.getCardMessageText], in particular that a
 * job-level cap is reported on multi-payment jobs rather than being skipped in favour of the
 * per-unit warnings.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectJobRecordCardMessageTest {
    private val visitDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
        // Reads the Connect task table, which has no bearing on the visit-limit branches under test.
        mockkStatic(ConnectTaskUtils::class)
        every { ConnectTaskUtils.shouldShowTasksCompletedMessage(any(), any()) } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun daysFromNow(days: Int): Date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.time

    /** A multi-payment job whose units are generously capped, so only the job caps can bind. */
    private fun multiPaymentJob(
        maxVisits: Int = 100,
        maxDailyVisits: Int = 100,
    ) = ConnectJobRecord().apply {
        setIsActive(true)
        setIsUserSuspended(false)
        projectEndDate = daysFromNow(30)
        setMaxVisits(maxVisits)
        setMaxDailyVisits(maxDailyVisits)
        deliveries = emptyList()
        paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 50, maxDaily = 50),
                paymentUnit(id = 2, maxTotal = 50, maxDaily = 50),
            )
    }

    private fun paymentUnit(
        id: Int,
        maxTotal: Int,
        maxDaily: Int,
    ): ConnectPaymentUnitRecord =
        ConnectPaymentUnitRecord.fromJson(
            JSONObject(
                """
                {
                    "id": $id,
                    "payment_unit_id": "unit-$id",
                    "name": "Unit $id",
                    "max_total": $maxTotal,
                    "max_daily": $maxDaily,
                    "amount": 100
                }
                """.trimIndent(),
            ),
            ConnectJobRecord(),
        )

    private fun delivery(
        id: Int,
        unitId: Int,
        date: Date,
    ): ConnectJobDeliveryRecord =
        ConnectJobDeliveryRecord.fromJson(
            JSONObject(
                """
                {
                    "id": $id,
                    "visit_date": "${visitDateFormat.format(date)}",
                    "status": "approved",
                    "deliver_unit_name": "Unit $unitId",
                    "deliver_unit_slug": "unit-$unitId",
                    "entity_id": "entity-$id",
                    "entity_name": "Entity $id",
                    "reason": "",
                    "deliver_unit_slug_id": "unit-$unitId"
                }
                """.trimIndent(),
            ),
            ConnectJobRecord(),
        )

    @Test
    fun `a multi-payment job at the job-level total reports it even with units under their limits`() {
        val job = multiPaymentJob(maxVisits = 2)
        job.deliveries =
            listOf(
                delivery(1, 1, daysFromNow(-2)),
                delivery(2, 2, daysFromNow(-1)),
            )

        assertEquals(
            context.getString(R.string.connect_progress_warning_max_reached_single),
            job.getCardMessageText(context),
        )
    }

    @Test
    fun `a multi-payment job at the job-level daily total reports it even with units under their limits`() {
        val job = multiPaymentJob(maxDailyVisits = 1)
        job.deliveries = listOf(delivery(1, 1, Date()))

        assertEquals(
            context.getString(R.string.connect_progress_warning_daily_max_reached_single),
            job.getCardMessageText(context),
        )
    }

    @Test
    fun `a multi-payment job under the job caps still names the unit that is out of visits`() {
        val job = multiPaymentJob()
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 1, maxDaily = 50),
                paymentUnit(id = 2, maxTotal = 50, maxDaily = 50),
            )
        job.deliveries = listOf(delivery(1, 1, daysFromNow(-2)))

        assertEquals(
            context.getString(R.string.connect_progress_warning_max_reached_multi, "Unit 1"),
            job.getCardMessageText(context),
        )
    }

    @Test
    fun `a multi-payment job with room everywhere reports nothing`() {
        val job = multiPaymentJob()
        job.deliveries = listOf(delivery(1, 1, Date()))

        assertNull(job.getCardMessageText(context))
    }
}
