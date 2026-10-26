package org.commcare.fragments.personalId

import android.Manifest
import android.app.Activity
import android.app.Application
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.shadows.ShadowPhoneAuthProvider
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.dalvik.R
import org.commcare.personalId.BaseNavGraphToolbarTitleTest
import org.commcare.personalId.TitledScreen
import org.commcare.utils.MockAndroidKeyStoreProvider
import org.commcare.utils.OtpManager
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(
    application = CommCareTestApplication::class,
    sdk = [31],
    shadows = [ShadowGoogleApiAvailability::class, ShadowPhoneAuthProvider::class],
)
@RunWith(AndroidJUnit4::class)
class PersonalIdActivityToolbarTitleTest : BaseNavGraphToolbarTitleTest() {
    private val activityLauncher = ActivityLauncher()

    override val graphRes = R.navigation.nav_graph_personalid
    override val screens = SCREENS
    override val currentActivity: Activity get() = activityLauncher.currentActivity()
    override val hostNavController: NavController get() = activityLauncher.hostNavController()

    @Before
    fun setUp() {
        MockAndroidKeyStoreProvider.registerProvider()
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
        shadowOf(context as Application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        shadowOf(context.getSystemService(LocationManager::class.java))
            .setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        activityLauncher.setUp()
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
    }

    @After
    fun tearDown() {
        activityLauncher.tearDown()
        MockAndroidKeyStoreProvider.deregisterProvider()
    }

    override fun launchHostActivity() = activityLauncher.launchHostActivity(buildSessionData())

    private fun buildSessionData() =
        PersonalIdSessionData(
            requiredLock = PersonalIdSessionData.PIN,
            token = "test_session_token",
            accountExists = true,
            userName = "Test User",
            phoneNumber = "+11234567890",
            maskedEmail = "u***@example.com",
            smsMethod = OtpManager.SMS_METHOD_PERSONAL_ID,
        )

    private class ActivityLauncher : BasePersonalIdConfigurationTest<BasePersonalIdFragment>() {
        private var activityLaunched = false

        fun currentActivity(): Activity = activity

        fun hostNavController(): NavController = navHostFragment.navController

        fun launchHostActivity(sessionData: PersonalIdSessionData) {
            destroyActivity()
            bootActivityAndSeedSession(sessionData)
            activityController.visible()
            ShadowLooper.idleMainLooper()
            activityLaunched = true
        }

        override fun tearDown() {
            destroyActivity()
            super.tearDown()
        }

        private fun destroyActivity() {
            if (activityLaunched && !activity.isDestroyed) activityController.pause().stop().destroy()
            activityLaunched = false
        }
    }

    companion object {
        private const val EMAIL = "user@example.com"

        private val SCREENS: Map<Int, TitledScreen> =
            listOf(
                TitledScreen(R.id.personalid_phone_fragment, R.string.connect_registration_title),
                TitledScreen(
                    R.id.personalid_biometric_config,
                    R.string.connect_appbar_title_app_lock,
                ),
                TitledScreen(R.id.personalid_otp_page, R.string.connect_verify_phone_title),
                TitledScreen(
                    R.id.personalid_backup_code,
                    R.string.connect_backup_code_title_confirm,
                ),
                TitledScreen(R.id.personalid_name, R.string.personalid_name_appbar_title),
                TitledScreen(R.id.personalid_photo_capture, R.string.personalid_capture_photo),
                TitledScreen(
                    R.id.personalid_email,
                    R.string.personalid_email_appbar_title,
                    Bundle().apply { putSerializable("workflow", EmailWorkFlow.RECOVERY) },
                ),
                TitledScreen(
                    R.id.personalid_send_email_otp_fragment,
                    R.string.personalid_send_email_otp_title,
                    Bundle().apply {
                        putString("email", EMAIL)
                        putSerializable("workflow", EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY)
                    },
                ),
                TitledScreen(
                    R.id.personalid_email_verification,
                    R.string.personalid_email_verification_appbar_title,
                    Bundle().apply {
                        putString("email", EMAIL)
                        putSerializable("workflow", EmailWorkFlow.FORGOT_BACKUP_CODE_RECOVERY)
                        putInt("emailOtpRequestCount", 1)
                    },
                ),
                TitledScreen(
                    R.id.personalid_account_config_set_new_backup_code_fragment,
                    R.string.personalid_set_new_backup_code_title,
                ),
            ).associateBy { it.destinationId }
    }
}
