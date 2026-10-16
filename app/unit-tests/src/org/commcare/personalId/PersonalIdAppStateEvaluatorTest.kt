package org.commcare.personalId

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.PersonalIdSessionManager.ConnectAppManagement
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

/**
 * [PersonalIdAppStateEvaluator] decides how an app relates to PersonalID.
 *
 * The [ConnectAppManagement.Connect] branch needs a seated CommCare app and a synced Connect job,
 * so it is covered by the home screen tests that install one rather than here.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdAppStateEvaluatorTest {
    private val appId = "test-app-id"
    private val username = "test-user"

    @Before
    fun setUp() {
        PersonalIdSessionManager.clearInstance()
    }

    @After
    fun tearDown() {
        PersonalIdSessionManager.clearInstance()
        ConnectDatabaseHelper.teardown()
    }

    private fun signIn() {
        ConnectTestUtils.createConnectDbFile()
        ConnectUserDatabaseUtil.storeUser(
            ConnectUserRecord("", username, "", "Test User", "", Date(), null, false, "", false),
        )
    }

    @Test
    fun `an app with no record and no credentials is Unmanaged`() {
        signIn()
        assertEquals(ConnectAppManagement.Unmanaged, PersonalIdAppStateEvaluator.evaluateAppState(appId, username))
    }

    @Test
    fun `an app PersonalID holds credentials for is PersonalId managed`() {
        signIn()
        ConnectAppDatabaseUtil.storeApp(appId, username, true, "app-password", false)
        assertEquals(ConnectAppManagement.PersonalId, PersonalIdAppStateEvaluator.evaluateAppState(appId, username))
    }

    @Test
    fun `a linked app with no stored password is Unmanaged`() {
        signIn()
        ConnectAppDatabaseUtil.storeApp(appId, username, true, "", false)
        assertEquals(ConnectAppManagement.Unmanaged, PersonalIdAppStateEvaluator.evaluateAppState(appId, username))
    }

    @Test
    fun `everything is Unmanaged while PersonalID is signed out`() {
        assertEquals(ConnectAppManagement.Unmanaged, PersonalIdAppStateEvaluator.evaluateAppState(appId, username))
    }

    @Test
    fun `isSeatedAppWorkerLinked is false while signed out`() {
        assertFalse(PersonalIdAppStateEvaluator.isSeatedAppWorkerLinked(username))
    }

    @Test
    fun `isSessionLoggedIntoApp is false when no user session is active`() {
        signIn()
        assertFalse(PersonalIdAppStateEvaluator.isSessionLoggedIntoApp(appId))
    }
}
