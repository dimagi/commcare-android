package org.commcare.personalId

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.personalId.SsoToken
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
 * [PersonalIdCredentialProvider] reads stored credentials. Everything it returns is gated on
 * PersonalID being signed in, and on the token not having expired.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdCredentialProviderTest {
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

    private fun signIn(tokenExpiry: Date = ConnectTestUtils.daysFromNow(1)) {
        ConnectTestUtils.createConnectDbFile()
        val user = ConnectUserRecord("", username, "", "Test User", "", Date(), null, false, "", false)
        user.updateConnectToken("stored-connect-token", tokenExpiry)
        ConnectUserDatabaseUtil.storeUser(user)
    }

    private fun linkApp(
        password: String = "app-password",
        hqTokenExpiry: Date? = null,
    ) {
        val record = ConnectAppDatabaseUtil.storeApp(appId, username, true, password, false)
        if (hqTokenExpiry != null) {
            record.updateHqToken(SsoToken("stored-hq-token", hqTokenExpiry))
            ConnectAppDatabaseUtil.storeApp(record)
        }
    }

    // ========== getConnectToken ==========

    @Test
    fun `getConnectToken returns null when PersonalID is not signed in`() {
        assertNull(PersonalIdCredentialProvider.getConnectToken())
    }

    @Test
    fun `getConnectToken returns the stored token while it is unexpired`() {
        signIn()
        assertEquals("stored-connect-token", PersonalIdCredentialProvider.getConnectToken()?.bearerToken)
    }

    @Test
    fun `getConnectToken returns null once the token has expired`() {
        signIn(tokenExpiry = ConnectTestUtils.daysFromNow(-1))
        assertNull(PersonalIdCredentialProvider.getConnectToken())
    }

    // ========== linked app credentials ==========

    @Test
    fun `isPersonalIdLinkedApp is false when PersonalID is not signed in`() {
        assertFalse(PersonalIdCredentialProvider.isPersonalIdLinkedApp(appId, username))
    }

    @Test
    fun `isPersonalIdLinkedApp is true for a linked app of a signed-in user`() {
        signIn()
        linkApp()
        assertTrue(PersonalIdCredentialProvider.isPersonalIdLinkedApp(appId, username))
    }

    @Test
    fun `getStoredPasswordForApp returns the stored password for a linked app`() {
        signIn()
        linkApp(password = "s3cret")
        assertEquals("s3cret", PersonalIdCredentialProvider.getStoredPasswordForApp(appId, username))
    }

    @Test
    fun `getStoredPasswordForApp returns null when the stored password is blank`() {
        signIn()
        linkApp(password = "")
        assertNull(PersonalIdCredentialProvider.getStoredPasswordForApp(appId, username))
    }

    @Test
    fun `getStoredPasswordForApp returns null for an app PersonalID does not manage`() {
        signIn()
        assertNull(PersonalIdCredentialProvider.getStoredPasswordForApp("some-other-app", username))
    }

    // ========== HQ token ==========

    @Test
    fun `getTokenCredentialsForApp returns null when PersonalID is not signed in`() {
        assertNull(PersonalIdCredentialProvider.getTokenCredentialsForApp(appId, username))
    }

    @Test
    fun `getTokenCredentialsForApp returns the stored HQ token while it is unexpired`() {
        signIn()
        linkApp(hqTokenExpiry = ConnectTestUtils.daysFromNow(1))
        assertEquals(
            "stored-hq-token",
            PersonalIdCredentialProvider.getTokenCredentialsForApp(appId, username)?.bearerToken,
        )
    }

    @Test
    fun `getTokenCredentialsForApp returns null once the HQ token has expired`() {
        signIn()
        linkApp(hqTokenExpiry = ConnectTestUtils.daysFromNow(-1))
        assertNull(PersonalIdCredentialProvider.getTokenCredentialsForApp(appId, username))
    }
}
