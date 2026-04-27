package org.commcare.utils

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.DispatchActivity
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.commcaresupportlibrary.CommCareLauncher
import org.commcare.connect.ConnectAppUtils
import org.commcare.connect.ConnectConstants
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.utils.FirebaseMessagingUtil.handleNotification
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SessionEndpointNotificationTest {
    private val context: Context = CommCareTestApplication.instance()

    private val learnAppId = "learn-app"
    private val deliveryAppId = "delivery-app"
    private val opportunityUuidValue = "opp-1"
    private val endpointIdValue = "ep-1"

    private lateinit var connectJobUtilsMock: MockedStatic<ConnectJobUtils>
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus

    @Before
    fun setUp() {
        // Set PersonalIdManager status to LoggedIn directly to satisfy cccCheckPassed().
        // init() only touches the DB when status == NotIntroduced; once LoggedIn it's a no-op.
        val manager = PersonalIdManager.getInstance()
        savedStatus = manager.getStatus()
        manager.setStatus(PersonalIdManager.PersonalIdStatus.LoggedIn)

        connectJobUtilsMock = Mockito.mockStatic(ConnectJobUtils::class.java)
        connectJobUtilsMock
            .`when`<String> {
                ConnectJobUtils.getAppIdForOpportunity(
                    Mockito.any(),
                    Mockito.eq(opportunityUuidValue),
                    Mockito.eq(ConnectConstants.OPPORTUNITY_STATUS_LEARN),
                )
            }.thenReturn(learnAppId)
        connectJobUtilsMock
            .`when`<String> {
                ConnectJobUtils.getAppIdForOpportunity(
                    Mockito.any(),
                    Mockito.eq(opportunityUuidValue),
                    Mockito.eq(ConnectConstants.OPPORTUNITY_STATUS_DELIVERY),
                )
            }.thenReturn(deliveryAppId)
    }

    @After
    fun tearDown() {
        connectJobUtilsMock.close()
        PersonalIdManager.getInstance().setStatus(savedStatus)
    }

    private fun buildPayload(
        action: String = "ccc_generic_opportunity",
        sessionEndpointId: String? = endpointIdValue,
        opportunityUuid: String? = opportunityUuidValue,
        opportunityStatus: String? = ConnectConstants.OPPORTUNITY_STATUS_LEARN,
    ): Map<String, String> {
        val map =
            mutableMapOf(
                "action" to action,
                "title" to "Test",
                "body" to "Test body",
            )
        if (sessionEndpointId != null) {
            map[PushNotificationRecord.META_SESSION_ENDPOINT_ID] = sessionEndpointId
        }
        if (opportunityUuid != null) {
            map["opportunity_uuid"] = opportunityUuid
        }
        if (opportunityStatus != null) {
            map["opportunity_status"] = opportunityStatus
        }
        return map
    }

    @Test
    fun `ccc_generic_opportunity with session_endpoint_id routes to DispatchActivity`() {
        val payload = buildPayload()
        val intent = handleNotification(context, payload, null, false)
        assertEquals(
            DispatchActivity::class.java.name,
            intent!!.component!!.className,
        )
        assertEquals(endpointIdValue, intent.getStringExtra(DispatchActivity.SESSION_ENDPOINT_ID))
        assertTrue(intent!!.getBooleanExtra(ConnectAppUtils.IS_LAUNCH_FROM_CONNECT, false))
    }

    @Test
    fun `SESSION_ENDPOINT_APP_ID uses learn appId for learn status`() {
        val payload = buildPayload(opportunityStatus = ConnectConstants.OPPORTUNITY_STATUS_LEARN)
        val intent = handleNotification(context, payload, null, false)
        assertEquals(learnAppId, intent!!.getStringExtra(CommCareLauncher.SESSION_ENDPOINT_APP_ID))
    }

    @Test
    fun `SESSION_ENDPOINT_APP_ID uses delivery appId for delivery status`() {
        val payload = buildPayload(opportunityStatus = ConnectConstants.OPPORTUNITY_STATUS_DELIVERY)
        val intent = handleNotification(context, payload, null, false)
        assertEquals(deliveryAppId, intent!!.getStringExtra(CommCareLauncher.SESSION_ENDPOINT_APP_ID))
    }

    @Test
    fun `ccc_generic_opportunity without session_endpoint_id routes to ConnectActivity`() {
        val payload = buildPayload(sessionEndpointId = null)
        val intent = handleNotification(context, payload, null, false)
        assertEquals(
            ConnectActivity::class.java.name,
            intent!!.component!!.className,
        )
    }

    @Test
    fun `missing opportunityUUID falls back to ConnectActivity`() {
        connectJobUtilsMock
            .`when`<String> {
                ConnectJobUtils.getAppIdForOpportunity(
                    Mockito.any(),
                    Mockito.isNull(),
                    Mockito.any(),
                )
            }.thenReturn(null)

        val payload = buildPayload(opportunityUuid = null)
        val intent = handleNotification(context, payload, null, false)
        assertEquals(
            ConnectActivity::class.java.name,
            intent!!.component!!.className,
        )
    }

    @Test
    fun `job not found falls back to ConnectActivity`() {
        connectJobUtilsMock
            .`when`<String> {
                ConnectJobUtils.getAppIdForOpportunity(
                    Mockito.any(),
                    Mockito.eq(opportunityUuidValue),
                    Mockito.any(),
                )
            }.thenReturn(null)

        val payload = buildPayload()
        val intent = handleNotification(context, payload, null, false)
        assertEquals(
            ConnectActivity::class.java.name,
            intent!!.component!!.className,
        )
    }
}
