package org.commcare.login

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.commcare.CommCareApplication
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.engine.resource.installers.SingleAppInstallation
import org.commcare.network.DataPullRequester
import org.commcare.network.LocalReferencePullResponseFactory
import org.commcare.preferences.ServerUrls
import org.commcare.suite.model.OfflineUserRestore
import org.commcare.utils.AndroidCommCarePlatform
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncOperationsTest {
    private val commCareApplication = mockk<CommCareApplication>(relaxed = true)
    private val syncOperations = SyncOperations(context = mockk(relaxed = true))

    @Before
    fun setUp() {
        mockkStatic(CommCareApplication::class)
        mockkStatic(ServerUrls::class)
        every { CommCareApplication.instance() } returns commCareApplication
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `NORMAL mode pulls from the data server with the default requester`() {
        val defaultRequester = mockk<DataPullRequester>()
        every { ServerUrls.getDataServerKey() } returns "https://example.com/ota"
        every { commCareApplication.dataPullRequester } returns defaultRequester

        val plan = syncOperations.resolvePullPlan(DataPullMode.NORMAL)

        assertEquals("https://example.com/ota", plan.server)
        assertNull(plan.userId)
        assertSame(defaultRequester, plan.requester)
        assertFalse(plan.blockRemoteKeyManagement)
        assertTrue(plan.payloadReferences.isEmpty())
    }

    @Test
    fun `CONSUMER_APP mode does a local restore from the bundled reference`() {
        val plan = syncOperations.resolvePullPlan(DataPullMode.CONSUMER_APP)

        assertEquals("fake-server-that-is-never-used", plan.server)
        assertEquals("unused", plan.userId)
        assertSame(LocalReferencePullResponseFactory.INSTANCE, plan.requester)
        assertTrue(plan.blockRemoteKeyManagement)
        assertEquals(
            listOf(SingleAppInstallation.LOCAL_RESTORE_REFERENCE),
            plan.payloadReferences,
        )
    }

    @Test
    fun `CCZ_DEMO mode does a local restore from the demo user reference`() {
        val platform = mockk<AndroidCommCarePlatform>()
        val demoUserRestore = mockk<OfflineUserRestore>()
        every { commCareApplication.commCarePlatform } returns platform
        every { platform.demoUserRestore } returns demoUserRestore
        every { demoUserRestore.reference } returns "jr://asset/demo_user_restore.xml"

        val plan = syncOperations.resolvePullPlan(DataPullMode.CCZ_DEMO)

        assertEquals("fake-server-that-is-never-used", plan.server)
        assertEquals("demo_id", plan.userId)
        assertSame(LocalReferencePullResponseFactory.INSTANCE, plan.requester)
        assertTrue(plan.blockRemoteKeyManagement)
        assertEquals(listOf("jr://asset/demo_user_restore.xml"), plan.payloadReferences)
    }
}
