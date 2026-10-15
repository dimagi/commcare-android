package org.commcare.connect

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

/**
 * The session core of [PersonalIdSessionManager]: what state it reports, and the transitions
 * between those states. Everything else moved out under CCCT-2654.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdSessionManagerTest {
    @Before
    fun resetSingleton() {
        PersonalIdSessionManager.clearInstance()
    }

    @After
    fun tearDown() {
        PersonalIdSessionManager.clearInstance()
        ConnectDatabaseHelper.teardown()
    }

    private fun storeUser(registrationPhase: Int = ConnectConstants.PERSONAL_ID_USER_STATUS_REGISTERED) {
        ConnectTestUtils.createConnectDbFile()
        val user =
            ConnectUserRecord("", "", "", "Test User", "", Date(), null, false, "", false)
        user.registrationPhase = registrationPhase
        ConnectUserDatabaseUtil.storeUser(user)
    }

    @Test
    fun `status is NotIntroduced when no user has been stored`() {
        assertEquals(
            PersonalIdSessionManager.PersonalIdStatus.NotIntroduced,
            PersonalIdSessionManager.getInstance().status,
        )
        assertFalse(PersonalIdSessionManager.getInstance().isloggedIn())
    }

    @Test
    fun `a registered user resolves to LoggedIn`() {
        storeUser()
        PersonalIdSessionManager.getInstance().init()
        assertEquals(
            PersonalIdSessionManager.PersonalIdStatus.LoggedIn,
            PersonalIdSessionManager.getInstance().status,
        )
    }

    @Test
    fun `a user mid-registration resolves to Registering`() {
        storeUser(registrationPhase = ConnectConstants.PERSONAL_ID_USER_STATUS_REGISTERED - 1)
        PersonalIdSessionManager.getInstance().init()
        assertEquals(
            PersonalIdSessionManager.PersonalIdStatus.Registering,
            PersonalIdSessionManager.getInstance().status,
        )
        assertFalse(PersonalIdSessionManager.getInstance().isloggedIn())
    }

    /**
     * The point of the CCCT-2654 change: callers no longer have to run [init] first. Before it,
     * this returned a stale false.
     */
    @Test
    fun `isloggedIn resolves the session without a prior init`() {
        storeUser()
        assertTrue(PersonalIdSessionManager.getInstance().isloggedIn())
    }

    @Test
    fun `forgetUser clears the stored user and resets the status`() {
        storeUser()
        val manager = PersonalIdSessionManager.getInstance()
        assertTrue(manager.isloggedIn())

        manager.forgetUser("test")

        assertEquals(PersonalIdSessionManager.PersonalIdStatus.NotIntroduced, manager.status)
        assertFalse(manager.isloggedIn())
        assertNull(ConnectUserDatabaseUtil.getUser())
    }

    @Test
    fun `getInstance returns the same instance until it is cleared`() {
        val first = PersonalIdSessionManager.getInstance()
        assertTrue(first === PersonalIdSessionManager.getInstance())

        PersonalIdSessionManager.clearInstance()
        assertFalse(first === PersonalIdSessionManager.getInstance())
    }
}
