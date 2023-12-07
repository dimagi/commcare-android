package org.commcare.utils

import android.security.keystore.KeyGenParameterSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.util.TestAppInstaller
import org.commcare.util.EncryptionUtils
import org.javarosa.core.model.User
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
public class EncryptCredentialsInMemoryTest {

    @Before
    fun setup() {
        TestAppInstaller.installAppAndUser(
            "jr://resource/commcare-apps/update_tests/base_app/profile.ccpr",
            TEST_USER,
            TEST_PASS
        )

        // Set production encryption key provider
        EncryptionUtils.setEncryptionKeyProvider(EncryptionKeyProvider())
    }

    @Test
    fun saveUsernameWithKeyStoreAndReadWithout_shouldPass() {
        // confirm that there is no android key store available
        Assert.assertFalse(EncryptionUtils.getEncryptionKeyProvider().isKeyStoreAvailable)

        // register mock Android key store provider, this is when the key store becomes available
        MockAndroidKeyStoreProvider.registerProvider()

        // generate key to encrypt User credentials in the key store
        generateUserCredentialKey()

        // assert that the android key store is available
        Assert.assertTrue(EncryptionUtils.getEncryptionKeyProvider().isKeyStoreAvailable)

        // login with the Android key store available
        TestAppInstaller.login(TEST_USER, TEST_PASS)

        // retrieve the logged in user, this should be using the encrypted version
        var user = CommCareApplication.instance().getSession().getLoggedInUser()

        // save the same username and store the username for future comparison
        user.setUsername(TEST_USER)
        CommCareApplication.instance().getRawStorage(
            "USER",
            User::class.java,
            CommCareApplication.instance().userDbHandle
        ).write(user)
        val username = user.username

        // close the user session
        CommCareApplication.instance().closeUserSession()

        // deregister the mock Android key store provider, key store is no longer available
        MockAndroidKeyStoreProvider.deregisterProvider()

        // confirm that the key store is no longer available
        Assert.assertFalse(EncryptionUtils.getEncryptionKeyProvider().isKeyStoreAvailable)

        // login once again, this time without the keystore
        TestAppInstaller.login(TEST_USER, TEST_PASS)

        // retrieve the current logged in user
        user = CommCareApplication.instance().session.loggedInUser

        // confirm that the previously captured username matches the current user's
        Assert.assertEquals(username, user.username)
    }

    @After
    fun restore() {
        EncryptionUtils.reloadEncryptionKeyProvider()
    }

    private fun generateUserCredentialKey() {
        val mockKeyGenParameterSpec = mockk<KeyGenParameterSpec>()
        every { mockKeyGenParameterSpec.keystoreAlias } returns EncryptionUtils.USER_CREDENTIALS_KEY_ALIAS

        // generate key using mock key generator
        val mockKeyGenerator = MockKeyGenerator()
        mockKeyGenerator.init(mockKeyGenParameterSpec)
        mockKeyGenerator.generateKey()
    }

    companion object {
        private const val TEST_USER = "test"
        private const val TEST_PASS = "123"
    }
}
