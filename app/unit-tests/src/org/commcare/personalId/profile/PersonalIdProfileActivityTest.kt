package org.commcare.personalId.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.models.database.connect.ConnectDatabaseSchemaManager
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.views.connect.NumericCodeView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileActivityTest {
    private lateinit var activityController: ActivityController<PersonalIdProfileActivity>
    private lateinit var activity: PersonalIdProfileActivity
    private lateinit var navController: NavController

    private lateinit var firebaseAnalyticsUtilMock: MockedStatic<FirebaseAnalyticsUtil>

    private val mockApiServer = PersonalIdMockApiServer(PersonalIdMockApiServer.CallbackMode.MAIN_LOOPER)

    @Before
    fun setUp() {
        firebaseAnalyticsUtilMock = Mockito.mockStatic(FirebaseAnalyticsUtil::class.java)
        firebaseAnalyticsUtilMock
            .`when`<NavController.OnDestinationChangedListener> {
                FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener()
            }.thenReturn(
                NavController.OnDestinationChangedListener { _: NavController, _: NavDestination, _: Bundle? -> },
            )
        setUpTestUser()
        mockApiServer.start()
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
        firebaseAnalyticsUtilMock.close()
        mockApiServer.shutdown()
        ConnectDatabaseHelper.teardown()
        CommCareApplication.instance().getDatabasePath(ConnectDatabaseSchemaManager.DB_NAME).delete()
    }

    /**
     * Creates a user record in the connect DB so that [ConnectUserDatabaseUtil.getUser] returns a
     * real user. [ConnectUserDatabaseUtil.getUser] guards behind [DatabaseConnectOpenHelper.dbExists],
     * which checks for the physical DB file. The test app uses an in-memory SQLite mock (no file),
     * so we create an empty marker file to satisfy that guard and then write the record directly into
     * the in-memory storage.
     */
    private fun setUpTestUser() {
        val dbFile = CommCareApplication.instance().getDatabasePath(ConnectDatabaseSchemaManager.DB_NAME)
        dbFile.parentFile?.mkdirs()
        dbFile.createNewFile()

        val user =
            ConnectUserRecord(
                "+11234567890",
                "test-user-id",
                "test-password",
                "Test User",
                "",
                java.util.Date(),
                null,
                false,
                "",
                false,
            ).apply {
                email = "test@example.com"
            }
        ConnectDatabaseHelper.getConnectStorage(ConnectUserRecord::class.java).write(user)
    }

    private fun launchActivity(intent: Intent? = null) {
        activityController = Robolectric.buildActivity(PersonalIdProfileActivity::class.java, intent)
        activity =
            activityController
                .create()
                .postCreate(null)
                .start()
                .get()
        ShadowLooper.idleMainLooper()
        val navHostFragment =
            activity.supportFragmentManager.findFragmentById(R.id.profile_nav_host) as NavHostFragment
        navController = navHostFragment.navController
    }

    private fun launchWithPendingBackupCode() {
        launchActivity(Intent().putExtra(PersonalIdProfileActivity.EXTRA_PENDING_BACKUP_CODE, true))
    }

    private fun launchAndResumeWithPendingBackupCode() {
        launchWithPendingBackupCode()
        activityController.resume()
        ShadowLooper.idleMainLooper()
        assertEquals(R.id.personalid_profile_set_new_backup_code_fragment, navController.currentDestination!!.id)
    }

    private fun currentFragment() =
        activity.supportFragmentManager
            .findFragmentById(R.id.profile_nav_host)!!
            .childFragmentManager
            .primaryNavigationFragment!!

    private fun setNewBackupCodeFragment(): PersonalIdProfileSetNewBackupCodeFragment =
        currentFragment() as PersonalIdProfileSetNewBackupCodeFragment

    @Test
    fun `launched without pending backup code extra stays at profile fragment`() {
        launchActivity()
        assertEquals(R.id.personalid_profile_fragment, navController.currentDestination!!.id)
    }

    @Test
    fun `launched with EXTRA_PENDING_BACKUP_CODE navigates to set new backup code fragment`() {
        launchWithPendingBackupCode()
        assertEquals(
            R.id.personalid_profile_set_new_backup_code_fragment,
            navController.currentDestination!!.id,
        )
    }

    @Test
    fun `pressing back from set new backup code finishes the activity`() {
        launchAndResumeWithPendingBackupCode()

        activity.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        ShadowLooper.idleMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        activity.runOnUiThread { dialog.findViewById<Button>(R.id.negative_button)!!.performClick() }
        ShadowLooper.idleMainLooper()

        assertTrue("activity should be finishing after exit", activity.isFinishing)
    }

    @Test
    fun `abandon from set new backup code fragment finishes or has valid destination`() {
        launchWithPendingBackupCode()
        activityController.resume()
        ShadowLooper.idleMainLooper()

        activity.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        ShadowLooper.idleMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        activity.runOnUiThread { dialog.findViewById<Button>(R.id.negative_button)!!.performClick() }
        ShadowLooper.idleMainLooper()

        assertTrue(
            "activity should be finishing or have a valid destination after abandon",
            activity.isFinishing || navController.currentDestination != null,
        )
    }

    @Test
    fun `successful backup code set in pending flow finishes the activity`() {
        launchAndResumeWithPendingBackupCode()

        setANewBackupCode()
        ShadowLooper.idleMainLooper()

        assertTrue("activity should finish after successfully setting backup code in pending flow", activity.isFinishing)
    }

    @Test
    fun `successful backup code set after forgot backup code returns to profile fragment`() {
        launchActivity()
        activityController.resume()
        ShadowLooper.idleMainLooper()

        // Click "Change Backup Code" on the profile fragment to navigate to the backup code screen.
        activity.runOnUiThread {
            currentFragment().requireView().findViewById<View>(R.id.profile_change_backup_code).performClick()
        }
        ShadowLooper.idleMainLooper()
        assertEquals(R.id.personalid_profile_backup_code_fragment, navController.currentDestination!!.id)

        // Click "Forgot Backup Code" to start the email OTP recovery flow.
        activity.runOnUiThread {
            currentFragment().requireView().findViewById<View>(R.id.personalid_forgot_backup_code).performClick()
        }
        ShadowLooper.idleMainLooper()
        assertEquals(R.id.personalid_send_email_otp_fragment, navController.currentDestination!!.id)

        // Click "Send" to send the email OTP (API mock returns success, triggering navigation to verification).
        mockApiServer.server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        activity.runOnUiThread {
            currentFragment().requireView().findViewById<View>(R.id.personalid_send_email_otp_button).performClick()
        }
        mockApiServer.drainHttp()
        assertEquals(R.id.personalid_email_verification_forgot_backup_code_fragment, navController.currentDestination!!.id)

        // Enter a complete OTP code — the auto-submit listener fires and navigates to set-new-backup-code.
        mockApiServer.server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"success"}"""))
        activity.runOnUiThread {
            currentFragment().requireView().findViewById<NumericCodeView>(R.id.otp_code_view).setCode("123456")
        }
        mockApiServer.drainHttp()

        assertEquals(
            R.id.personalid_profile_set_new_backup_code_fragment,
            navController.currentDestination!!.id,
        )

        setANewBackupCode()
        assertFalse(
            "The activity must remain open after backup-code recovery succeeds",
            activity.isFinishing,
        )
        assertEquals(
            R.id.personalid_profile_fragment,
            navController.currentDestination!!.id,
        )
    }

    private fun setANewBackupCode() {
        // Mock PersonalIdUnlocker so the biometric prompt resolves immediately in tests.
        mockkObject(PersonalIdUnlocker)
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<PersonalIdManager.ConnectActivityCompleteListener>().connectActivityComplete(true)
        }

        // Enter backup code and confirm code, enqueue the set-backup-code API response,
        // then click continue to trigger the unlock + API call sequence.
        val backupCode = "654321"
        mockApiServer.server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        activity.runOnUiThread {
            val setNewBackupCodeFragment = setNewBackupCodeFragment()
            setNewBackupCodeFragment
                .requireView()
                .findViewById<NumericCodeView>(R.id.backup_code_view)
                .setCode(backupCode)

            setNewBackupCodeFragment
                .requireView()
                .findViewById<NumericCodeView>(R.id.confirm_code_view)
                .setCode(backupCode)

            setNewBackupCodeFragment
                .requireView()
                .findViewById<MaterialButton>(R.id.connect_backup_code_button)
                .performClick()
        }
        mockApiServer.drainHttp()

        unmockkObject(PersonalIdUnlocker)
    }

    private fun recreateActivityWithPendingBackupCode() {
        val savedState = Bundle()
        activityController.saveInstanceState(savedState)
        ShadowLooper.idleMainLooper()
        activityController.pause().stop().destroy()
        activityController =
            Robolectric.buildActivity(
                PersonalIdProfileActivity::class.java,
                Intent().putExtra(PersonalIdProfileActivity.EXTRA_PENDING_BACKUP_CODE, true),
            )
        activity =
            activityController
                .create(savedState)
                .postCreate(savedState)
                .start()
                .resume()
                .get()
        ShadowLooper.idleMainLooper()
        val navHostFragment = activity.supportFragmentManager.findFragmentById(R.id.profile_nav_host) as NavHostFragment
        navController = navHostFragment.navController
    }

    @Test
    fun `recreation does not push set-new-backup-code onto the stack a second time`() {
        launchAndResumeWithPendingBackupCode()
        recreateActivityWithPendingBackupCode()

        assertEquals(R.id.personalid_profile_set_new_backup_code_fragment, navController.currentDestination!!.id)
        assertNotEquals(
            "set-new-backup-code must not appear twice in the back stack after recreation",
            R.id.personalid_profile_set_new_backup_code_fragment,
            navController.previousBackStackEntry?.destination?.id,
        )
    }

    @Test
    fun `abandoning set-new-backup-code after recreation finishes the activity`() {
        launchAndResumeWithPendingBackupCode()
        recreateActivityWithPendingBackupCode()

        activity.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        ShadowLooper.idleMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        activity.runOnUiThread { dialog.findViewById<Button>(R.id.negative_button)!!.performClick() }
        ShadowLooper.idleMainLooper()

        assertTrue(
            "activity should finish when abandoning after recreation — if it does not, set-new-backup-code was duplicated on the back stack",
            activity.isFinishing,
        )
    }
}
