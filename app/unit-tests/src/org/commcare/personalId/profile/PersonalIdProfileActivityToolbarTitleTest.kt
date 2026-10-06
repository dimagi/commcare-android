package org.commcare.personalId.profile

import android.app.Activity
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.shadows.ShadowPhoneAuthProvider
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.BaseNavGraphToolbarTitleTest
import org.commcare.personalId.TitledScreen
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(
    application = CommCareTestApplication::class,
    sdk = [31],
    shadows = [ShadowPhoneAuthProvider::class],
)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileActivityToolbarTitleTest : BaseNavGraphToolbarTitleTest() {
    private val mockApiServer =
        PersonalIdMockApiServer(PersonalIdMockApiServer.CallbackMode.MAIN_LOOPER)

    private lateinit var connectUserDatabaseUtilMock: MockedStatic<ConnectUserDatabaseUtil>
    private lateinit var firebaseAnalyticsUtilMock: MockedStatic<FirebaseAnalyticsUtil>
    private var activityController: ActivityController<PersonalIdProfileActivity>? = null

    override val graphRes = R.navigation.nav_graph_personalid_profile
    override val screens = SCREENS
    override val extraArrivalScreens = SEND_EMAIL_OTP_WORKFLOW_VARIANTS
    override val currentActivity: Activity get() = activityController!!.get()
    override lateinit var hostNavController: NavController

    @Before
    fun setUp() {
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
        ShadowPhoneAuthProvider.reset()
        connectUserDatabaseUtilMock = Mockito.mockStatic(ConnectUserDatabaseUtil::class.java)
        connectUserDatabaseUtilMock
            .`when`<ConnectUserRecord> { ConnectUserDatabaseUtil.getUser() }
            .thenReturn(
                ConnectUserRecord(
                    "+11234567890",
                    "test-user-id",
                    "test-password",
                    "Ada Lovelace",
                    "",
                    null,
                    null,
                    false,
                    "",
                    false,
                ).apply { email = EMAIL },
            )
        firebaseAnalyticsUtilMock = Mockito.mockStatic(FirebaseAnalyticsUtil::class.java)
        firebaseAnalyticsUtilMock
            .`when`<NavController.OnDestinationChangedListener> {
                FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener()
            }.thenReturn(
                NavController.OnDestinationChangedListener { _: NavController, _: NavDestination, _: Bundle? -> },
            )
        mockApiServer.start()
    }

    @After
    fun tearDown() {
        destroyActivity()
        connectUserDatabaseUtilMock.close()
        firebaseAnalyticsUtilMock.close()
        mockApiServer.shutdown()
        ShadowPhoneAuthProvider.reset()
    }

    override fun launch() {
        destroyActivity()
        val controller = Robolectric.buildActivity(PersonalIdProfileActivity::class.java)
        activityController = controller
        controller
            .create()
            .postCreate(null)
            .start()
            .resume()
            .visible()
        ShadowLooper.idleMainLooper()
        hostNavController =
            (controller.get().supportFragmentManager.findFragmentById(R.id.profile_nav_host) as NavHostFragment)
                .navController
    }

    private fun destroyActivity() {
        activityController?.let { if (!it.get().isDestroyed) it.pause().stop().destroy() }
        activityController = null
    }

    companion object {
        private const val EMAIL = "ada@example.com"

        private fun sendEmailOtp(
            workflow: EmailWorkFlow,
            titleRes: Int,
        ) = TitledScreen(
            R.id.personalid_send_email_otp_fragment,
            titleRes,
            Bundle().apply {
                putString("email", EMAIL)
                putSerializable("workflow", workflow)
            },
        )

        private val SCREENS: Map<Int, TitledScreen> =
            listOf(
                TitledScreen(R.id.personalid_profile_fragment, R.string.personalid_profile_title),
                TitledScreen(
                    R.id.personalid_profile_edit_fragment,
                    R.string.personalid_profile_edit_title,
                ),
                TitledScreen(
                    R.id.personalid_email_verification_fragment,
                    R.string.personalid_email_verification_appbar_title,
                    Bundle().apply {
                        putString("email", EMAIL)
                        putSerializable("workflow", EmailWorkFlow.EXISTING_USER)
                        putInt("emailOtpRequestCount", 1)
                    },
                ),
                TitledScreen(
                    R.id.personalid_email_verification_forgot_backup_code_fragment,
                    R.string.personalid_email_verification_appbar_title,
                    Bundle().apply {
                        putString("email", EMAIL)
                        putInt("emailOtpRequestCount", 1)
                    },
                ),
                TitledScreen(
                    R.id.personalid_profile_backup_code_fragment,
                    R.string.connect_backup_code_title_confirm,
                    Bundle().apply {
                        putSerializable(
                            "emailWorkflow",
                            EmailWorkFlow.EXISTING_USER,
                        )
                    },
                ),
                TitledScreen(
                    R.id.personalid_profile_send_phone_otp_fragment,
                    R.string.connect_verify_phone_title,
                    Bundle().apply { putString("pendingEmail", EMAIL) },
                ),
                TitledScreen(
                    R.id.personalid_profile_phone_verification_fragment,
                    R.string.connect_verify_phone_title,
                    Bundle().apply { putString("pendingEmail", EMAIL) },
                ),
                sendEmailOtp(
                    EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER,
                    R.string.personalid_send_email_otp_title,
                ),
                TitledScreen(
                    R.id.personalid_profile_set_new_backup_code_fragment,
                    R.string.personalid_set_new_backup_code_title,
                ),
            ).associateBy { it.destinationId }

        private val SEND_EMAIL_OTP_WORKFLOW_VARIANTS =
            listOf(
                sendEmailOtp(
                    EmailWorkFlow.PENDING_BACKUP_CODE,
                    R.string.personalid_send_email_otp_pending_backup_code_title,
                ),
                sendEmailOtp(
                    EmailWorkFlow.EXISTING_USER,
                    R.string.personalid_email_verification_title,
                ),
            )
    }
}
