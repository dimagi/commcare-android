package org.commcare.personalId.profile

import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
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
import org.mockito.kotlin.any
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SetNewBackupCodeFragmentTest : BasePersonalIdProfileTest() {
    @Before
    fun navigateToSetNewBackupCodeScreen() {
        mockkObject(PersonalIdUnlocker)
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<PersonalIdManager.ConnectActivityCompleteListener>().connectActivityComplete(true)
        }

        // Prevent SharedPreferences lockout state from a previous test blocking navigation
        PersonalIdUserPreferences.clearBackupCodeLockout()
        user.pin = "123456" // 6-digit pin so ProfileBackupCode accepts it
        onUiThread { navController.navigate(R.id.action_profile_to_profile_backup_code) }
        // Enter the current backup code on the confirm screen and click continue
        onUiThread { backupCodeViewOnConfirmScreen().setCode("123456") }
        onUiThread { continueButtonOnConfirmScreen().performClick() }
        // Now we should be on personalid_set_new_backup_code_fragment
    }

    @After
    fun tearDownMocks() {
        unmockkObject(PersonalIdUnlocker)
    }

    private fun fragment() = navHostFragment.childFragmentManager.primaryNavigationFragment as SetNewBackupCodeFragment

    private fun backupCodeViewOnConfirmScreen(): NumericCodeView {
        val confirmFragment = navHostFragment.childFragmentManager.primaryNavigationFragment
        return confirmFragment!!.requireView().findViewById(R.id.backup_code_view)
    }

    private fun continueButtonOnConfirmScreen(): MaterialButton {
        val confirmFragment = navHostFragment.childFragmentManager.primaryNavigationFragment
        return confirmFragment!!.requireView().findViewById(R.id.connect_backup_code_button)
    }

    private fun backupCodeView(): NumericCodeView = fragment().requireView().findViewById(R.id.backup_code_view)

    private fun confirmCodeView(): NumericCodeView = fragment().requireView().findViewById(R.id.confirm_code_view)

    private fun confirmCodeLayout(): View = fragment().requireView().findViewById(R.id.confirm_code_layout)

    private fun confirmCodeLabel(): View = fragment().requireView().findViewById(R.id.confirm_code_label)

    private fun welcomeBackLayout(): View = fragment().requireView().findViewById(R.id.welcome_back_layout)

    private fun notMeButton(): View = fragment().requireView().findViewById(R.id.not_me_button)

    private fun continueButton(): MaterialButton = fragment().requireView().findViewById(R.id.connect_backup_code_button)

    private fun errorMessage(): TextView = fragment().requireView().findViewById(R.id.connect_backup_code_error_message)

    private fun performSuccessfulSave(newCode: String = "654321") {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        setCodesAndContinue(newCode)
        mockApiServer.drainHttp()
    }

    private fun setCodesAndContinue(newCode: String) {
        onUiThread { backupCodeView().setCode(newCode) }
        onUiThread { confirmCodeView().setCode(newCode) }
        onUiThread { continueButton().performClick() }
    }

    // ===== Initial state =====

    @Test
    fun `confirm code layout is visible`() {
        assertEquals(View.VISIBLE, confirmCodeLayout().visibility)
    }

    @Test
    fun `confirm code label is visible`() {
        assertEquals(View.VISIBLE, confirmCodeLabel().visibility)
    }

    @Test
    fun `welcome back layout is hidden`() {
        assertEquals(View.GONE, welcomeBackLayout().visibility)
    }

    @Test
    fun `not me button is hidden`() {
        assertEquals(View.GONE, notMeButton().visibility)
    }

    @Test
    fun `continue button starts disabled`() {
        assertFalse(continueButton().isEnabled)
    }

    @Test
    fun `error message starts hidden`() {
        assertEquals(View.GONE, errorMessage().visibility)
    }

    // ===== Validation =====

    @Test
    fun `continue button enables when both codes match`() {
        onUiThread { backupCodeView().setCode("654321") }
        onUiThread { confirmCodeView().setCode("654321") }

        assertTrue(continueButton().isEnabled)
    }

    @Test
    fun `continue button stays disabled when codes differ`() {
        onUiThread { backupCodeView().setCode("654321") }
        onUiThread { confirmCodeView().setCode("000000") }

        assertFalse(continueButton().isEnabled)
    }

    @Test
    fun `mismatch error shown when both full and different`() {
        onUiThread { backupCodeView().setCode("654321") }
        onUiThread { confirmCodeView().setCode("000000") }

        assertEquals(View.VISIBLE, errorMessage().visibility)
        assertEquals(
            activity.getString(R.string.connect_backup_code_mismatch),
            errorMessage().text.toString(),
        )
    }

    @Test
    fun `mismatch error hidden when codes match`() {
        onUiThread { backupCodeView().setCode("654321") }
        onUiThread { confirmCodeView().setCode("654321") }

        assertEquals(View.GONE, errorMessage().visibility)
    }

    @Test
    fun `continue button stays disabled until both codes are 6 digits`() {
        onUiThread { backupCodeView().setCode("12345") }
        onUiThread { confirmCodeView().setCode("12345") }

        assertFalse(continueButton().isEnabled)
    }

    // ===== Success =====

    @Test
    fun `successful save pops to profile fragment`() {
        performSuccessfulSave()

        assertEquals(R.id.personalid_profile_fragment, currentDestinationId())
    }

    @Test
    fun `successful save shows success toast`() {
        performSuccessfulSave()

        assertEquals(
            activity.getString(R.string.personalid_backup_code_changed_success),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `successful save stores user`() {
        performSuccessfulSave()

        connectUserDatabaseUtilMock.verify {
            ConnectUserDatabaseUtil.storeUser(any())
        }
        assertEquals("654321", user.pin)
    }

    // ===== Error =====

    @Test
    fun `unlock failure does not submit and stays on screen`() {
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<PersonalIdManager.ConnectActivityCompleteListener>().connectActivityComplete(false)
        }

        setCodesAndContinue("654321")

        assertEquals(R.id.personalid_set_new_backup_code_fragment, currentDestinationId())
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `network error shows inline error and stays on screen`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        setCodesAndContinue("654321")

        mockApiServer.drainHttp()

        assertEquals(R.id.personalid_set_new_backup_code_fragment, currentDestinationId())
        assertEquals(View.VISIBLE, errorMessage().visibility)
        assertTrue(continueButton().isEnabled)
    }
}
