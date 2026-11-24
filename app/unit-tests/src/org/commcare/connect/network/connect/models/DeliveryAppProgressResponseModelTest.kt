package org.commcare.connect.network.connect.models

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.ConnectJobTestData
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.network.connect.parser.DeliveryAppProgressResponseParser
import org.javarosa.core.model.utils.DateUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.concurrent.TimeUnit

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DeliveryAppProgressResponseModelTest {
    private val context get() = ApplicationProvider.getApplicationContext<CommCareTestApplication>()

    @Before
    fun setUp() {
        ConnectTestUtils.signInToPersonalId(hasConnectAccess = true)
        val seeded = ConnectJobTestData.job().apply { status = ConnectJobRecord.STATUS_DELIVERING }
        ConnectJobUtils.storeJobs(context, listOf(seeded), true)
    }

    @After
    fun tearDown() {
        ConnectDatabaseHelper.teardown()
    }

    @Test
    fun `a delivery response does not restore an opportunity the opportunities refresh ended`() {
        val startedWith = storedJob()
        assertTrue(startedWith.isActive)
        endStoredJob()

        applyResponse("""{"payment_accrued": 7}""", startedWith)

        val stored = storedJob()
        assertFalse(stored.isActive)
        assertEquals(7, stored.paymentAccrued)
    }

    @Test
    fun `a delivery response without an end date keeps the end date the opportunities refresh stored`() {
        val startedWith = storedJob()
        val extendedEndDate = Date(startedWith.projectEndDate.time + TimeUnit.DAYS.toMillis(30))
        updateStoredJob { it.projectEndDate = extendedEndDate }

        applyResponse("""{"payment_accrued": 7}""", startedWith)

        assertEquals(extendedEndDate, storedJob().projectEndDate)
    }

    @Test
    fun `a delivery response with an end date stores it`() {
        val startedWith = storedJob()

        applyResponse("""{"end_date": "2028-06-30"}""", startedWith)

        assertEquals(DateUtils.parseDate("2028-06-30"), storedJob().projectEndDate)
    }

    private fun applyResponse(
        json: String,
        job: ConnectJobRecord,
    ) {
        DeliveryAppProgressResponseParser<DeliveryAppProgressResponseModel>()
            .parse(200, ByteArrayInputStream(json.toByteArray()), job)
            .applyToJob(job, context)
    }

    private fun storedJob(): ConnectJobRecord = ConnectJobUtils.getCompositeJob(ConnectJobTestData.JOB_UUID)!!

    private fun endStoredJob() = updateStoredJob { it.setIsActive(false) }

    /** Stands in for an opportunities refresh landing while the delivery request runs. */
    private fun updateStoredJob(change: (ConnectJobRecord) -> Unit) {
        val refreshed = storedJob()
        change(refreshed)
        ConnectJobUtils.upsertJob(refreshed)
    }
}
