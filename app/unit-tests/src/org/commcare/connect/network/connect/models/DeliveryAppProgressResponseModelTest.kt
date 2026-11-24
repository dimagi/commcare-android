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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

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

        val response =
            DeliveryAppProgressResponseParser<DeliveryAppProgressResponseModel>().parse(
                200,
                ByteArrayInputStream("""{"payment_accrued": 7}""".toByteArray()),
                startedWith,
            )
        response.applyToJob(startedWith, context)

        val stored = storedJob()
        assertFalse(stored.isActive)
        assertEquals(7, stored.paymentAccrued)
    }

    private fun storedJob(): ConnectJobRecord = ConnectJobUtils.getCompositeJob(ConnectJobTestData.JOB_UUID)!!

    private fun endStoredJob() {
        val ended = storedJob()
        ended.setIsActive(false)
        ConnectJobUtils.upsertJob(ended)
    }
}
