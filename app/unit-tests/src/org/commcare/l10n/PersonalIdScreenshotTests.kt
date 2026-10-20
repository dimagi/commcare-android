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
    private val locale: String,
) : BasePersonalIdPhoneFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    @Test
    fun phone() = L10nScreenshots.capture(activityController, locale, "01_phone")

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
    private val locale: String,
) : BasePersonalIdConfigurationTest<PersonalIdPhoneVerificationFragment>() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

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
        L10nScreenshots.capture(activityController, locale, "02_otp")
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
    private val locale: String,
) : BasePersonalIdNameFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    @Test
    fun name() = L10nScreenshots.capture(activityController, locale, "03_name")

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
    private val locale: String,
) : BasePersonalIdBackupCodeFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    @Test
    fun backupCodeNewUser() {
        launchBackupCodeFragment()
        L10nScreenshots.capture(activityController, locale, "04_backup_code_new")
    }

    @Test
    fun backupCodeRecovery() {
        launchBackupCodeFragment(buildSessionData(accountExists = true))
        L10nScreenshots.capture(activityController, locale, "05_backup_code_recovery")
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
    private val locale: String,
) : BasePersonalIdBiometricConfigFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    @Test
    fun biometricConfig() {
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
        setUpBiometricFragment()
        L10nScreenshots.capture(activityController, locale, "06_biometric_config")
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
    private val locale: String,
) : BasePersonalIdPhotoCaptureFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    @Test
    fun photoCapture() = L10nScreenshots.capture(activityController, locale, "07_photo_capture")

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
    private val locale: String,
) : BasePersonalIdEmailFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    @Test
    fun email() = L10nScreenshots.capture(activityController, locale, "08_email")

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
    private val locale: String,
) : BasePersonalIdEmailVerificationFragmentTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    @Test
    fun emailVerification() = L10nScreenshots.capture(activityController, locale, "09_email_verification")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}
