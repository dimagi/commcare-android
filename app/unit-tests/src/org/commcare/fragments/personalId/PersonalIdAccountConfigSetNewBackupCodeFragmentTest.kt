package org.commcare.fragments.personalId

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.views.connect.NumericCodeView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdAccountConfigSetNewBackupCodeFragmentTest :
    BasePersonalIdConfigurationTest<PersonalIdAccountConfigSetNewBackupCodeFragment>() {
    private lateinit var connectUserDatabaseUtilMock: MockedStatic<ConnectUserDatabaseUtil>

    @Before
    override fun setUp() {
        super.setUp()
        connectUserDatabaseUtilMock = Mockito.mockStatic(ConnectUserDatabaseUtil::class.java)
        connectUserDatabaseUtilMock
            .`when`<ConnectUserRecord> { ConnectUserDatabaseUtil.getUser() }
            .thenReturn(
                ConnectUserRecord(
                    "+11234567890",
                    "test-user-id",
                    "test-password",
                    "Test User",
                    "",
                    null,
                    null,
                    false,
                    "",
                    false,
                ),
            )
        mockkObject(PersonalIdUnlocker)
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<PersonalIdManager.ConnectActivityCompleteListener>().connectActivityComplete(true)
        }
        // State left behind by the email OTP step of the forgot-backup-code recovery flow.
        PersonalIdUserPreferences.setPendingBackupCode(true)

        navigateToFragment(sessionData(), R.id.personalid_account_config_set_new_backup_code_fragment)
        activity.runOnUiThread {
            installTestNavController(fragment.requireView(), R.id.personalid_account_config_set_new_backup_code_fragment)
        }
        ShadowLooper.idleMainLooper()
    }

    @After
    override fun tearDown() {
        activityController.pause().stop().destroy()
        PersonalIdUserPreferences.setPendingBackupCode(false)
        unmockkObject(PersonalIdUnlocker)
        connectUserDatabaseUtilMock.close()
        super.tearDown()
    }

    private fun sessionData() =
        PersonalIdSessionData(
            requiredLock = PersonalIdSessionData.PIN,
            demoUser = false,
            token = "test_session_token_abc",
            accountExists = true,
            userName = "Test User",
            phoneNumber = "+11234567890",
        )

    private fun backupCodeView(): NumericCodeView = fragment.requireView().findViewById(R.id.backup_code_view)

    private fun confirmCodeView(): NumericCodeView = fragment.requireView().findViewById(R.id.confirm_code_view)

    private fun continueButton(): MaterialButton = fragment.requireView().findViewById(R.id.connect_backup_code_button)

    private fun submitNewCode(response: MockResponse) {
        mockWebServer.enqueue(response)
        activity.runOnUiThread {
            backupCodeView().setCode("654321")
            confirmCodeView().setCode("654321")
            continueButton().performClick()
        }
        ShadowLooper.idleMainLooper()
        drainHttp()
    }

    @Test
    fun `setting the new backup code clears the pending backup code flag`() {
        submitNewCode(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(R.id.personalid_message_display, navController.currentDestination!!.id)
        assertFalse(
            "Pending flag must be cleared so the next launch does not ask for a backup code again",
            PersonalIdUserPreferences.isPendingBackupCode(),
        )
    }

    @Test
    fun `failing to set the new backup code keeps the pending backup code flag`() {
        submitNewCode(MockResponse().setResponseCode(500))

        assertEquals(R.id.personalid_account_config_set_new_backup_code_fragment, navController.currentDestination!!.id)
        assertTrue(PersonalIdUserPreferences.isPendingBackupCode())
    }
}
