package org.commcare.l10n

import androidx.biometric.BiometricManager
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.shadows.ShadowPhoneAuthProvider
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.dalvik.R
import org.commcare.fragments.personalId.BasePersonalIdBackupCodeFragmentTest
import org.commcare.fragments.personalId.BasePersonalIdBiometricConfigFragmentTest
import org.commcare.fragments.personalId.BasePersonalIdConfigurationTest
import org.commcare.fragments.personalId.BasePersonalIdEmailFragmentTest
import org.commcare.fragments.personalId.BasePersonalIdEmailVerificationFragmentTest
import org.commcare.fragments.personalId.BasePersonalIdNameFragmentTest
import org.commcare.fragments.personalId.BasePersonalIdPhoneFragmentTest
import org.commcare.fragments.personalId.BasePersonalIdPhotoCaptureFragmentTest
import org.commcare.fragments.personalId.PersonalIdPhoneVerificationFragment
import org.commcare.utils.OtpManager
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class PhoneScreenshotTest(
    private val target: ScreenshotTarget,
) : BasePersonalIdPhoneFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(target)

    @Test
    fun phone() = L10nScreenshots.capture(activityController, target, "01_phone")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}

@Config(application = CommCareTestApplication::class, sdk = [34], shadows = [ShadowPhoneAuthProvider::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class OtpScreenshotTest(
    private val target: ScreenshotTarget,
) : BasePersonalIdConfigurationTest<PersonalIdPhoneVerificationFragment>() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(target)

    override fun setUp() {
        super.setUp()
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
        ShadowPhoneAuthProvider.reset()
    }

    override fun tearDown() {
        ShadowPhoneAuthProvider.reset()
        activityController.pause().stop().destroy()
        super.tearDown()
    }

    @Test
    fun otp() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        navigateToFragment(
            PersonalIdSessionData(
                smsMethod = OtpManager.SMS_METHOD_PERSONAL_ID,
                otpFallback = false,
                phoneNumber = "+15555550123",
                token = "test-session-token",
            ),
            R.id.personalid_otp_page,
        )
        drainHttp()
        L10nScreenshots.capture(activityController, target, "02_otp")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class NameScreenshotTest(
    private val target: ScreenshotTarget,
) : BasePersonalIdNameFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(target)

    @Test
    fun name() = L10nScreenshots.capture(activityController, target, "03_name")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class BackupCodeScreenshotTest(
    private val target: ScreenshotTarget,
) : BasePersonalIdBackupCodeFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(target)

    @Test
    fun backupCodeNewUser() {
        launchBackupCodeFragment()
        L10nScreenshots.capture(activityController, target, "04_backup_code_new")
    }

    @Test
    fun backupCodeRecovery() {
        launchBackupCodeFragment(buildSessionData(accountExists = true))
        L10nScreenshots.capture(activityController, target, "05_backup_code_recovery")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class BiometricScreenshotTest(
    private val target: ScreenshotTarget,
) : BasePersonalIdBiometricConfigFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(target)

    @Test
    fun biometricConfig() {
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
        setUpBiometricFragment()
        L10nScreenshots.capture(activityController, target, "06_biometric_config")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class PhotoCaptureScreenshotTest(
    private val target: ScreenshotTarget,
) : BasePersonalIdPhotoCaptureFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(target)

    @Test
    fun photoCapture() = L10nScreenshots.capture(activityController, target, "07_photo_capture")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class EmailScreenshotTest(
    private val target: ScreenshotTarget,
) : BasePersonalIdEmailFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(target)

    @Test
    fun email() = L10nScreenshots.capture(activityController, target, "08_email")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class EmailVerificationScreenshotTest(
    private val target: ScreenshotTarget,
) : BasePersonalIdEmailVerificationFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(target)

    @Test
    fun emailVerification() = L10nScreenshots.capture(activityController, target, "09_email_verification")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}
