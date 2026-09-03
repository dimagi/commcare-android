package org.commcare.fragments.personalId

import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import com.google.android.material.button.MaterialButton
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.commcare.views.connect.NumericCodeView
import org.json.JSONObject
import org.junit.After
import org.robolectric.shadows.ShadowLooper

abstract class BasePersonalIdBackupCodeFragmentTest : BasePersonalIdConfigurationTest<PersonalIdBackupCodeFragment>() {
    protected lateinit var sessionData: PersonalIdSessionData

    protected fun buildSessionData(
        accountExists: Boolean = false,
        photoBase64: String? = null,
        email: String? = null,
    ): PersonalIdSessionData =
        PersonalIdSessionData(
            requiredLock = PersonalIdSessionData.PIN,
            demoUser = false,
            token = TEST_SESSION_TOKEN,
            accountExists = accountExists,
            userName = TEST_USER_NAME,
            phoneNumber = TEST_PHONE_NUMBER,
            photoBase64 = photoBase64,
            email = email,
        )

    protected fun launchBackupCodeFragment(sessionData: PersonalIdSessionData = buildSessionData()) {
        this.sessionData = sessionData
        navigateToFragment(sessionData, R.id.personalid_backup_code)
        activity.runOnUiThread {
            installTestNavController(fragment.requireView(), R.id.personalid_backup_code)
        }
        ShadowLooper.idleMainLooper()
    }

    @After
    @CallSuper
    override fun tearDown() {
        activityController.pause().stop().destroy()
        super.tearDown()
    }

    // ========== Views ==========

    protected val backupCodeView: NumericCodeView get() = findView(R.id.backup_code_view)

    protected val confirmCodeView: NumericCodeView get() = findView(R.id.confirm_code_view)

    protected val continueButton: MaterialButton get() = findView(R.id.connect_backup_code_button)

    protected val errorMessage: TextView get() = findView(R.id.connect_backup_code_error_message)

    protected val heading: TextView get() = findView(R.id.recovery_code_tilte)

    protected val subtitle: TextView get() = findView(R.id.backup_code_subtitle)

    protected val confirmCodeLabel: TextView get() = findView(R.id.confirm_code_label)

    protected val confirmCodeLayout: View get() = findView(R.id.confirm_code_layout)

    protected val welcomeBackLayout: View get() = findView(R.id.welcome_back_layout)

    protected val welcomeBackText: TextView get() = findView(R.id.welcome_back)

    protected val userPhoto: ImageView get() = findView(R.id.user_photo)

    protected val backupCodeVisibilityToggle: ImageView get() = findView(R.id.backup_code_visibility_toggle)

    protected val confirmCodeVisibilityToggle: ImageView get() = findView(R.id.confirm_code_visibility_toggle)

    protected val notMeButton: TextView get() = findView(R.id.not_me_button)

    private fun <T : View> findView(
        @IdRes id: Int,
    ): T = fragment.requireView().findViewById(id)

    // ========== Interactions ==========

    protected fun enterBackupCode(code: String) = enterCode(backupCodeView, code)

    protected fun enterConfirmCode(code: String) = enterCode(confirmCodeView, code)

    private fun enterCode(
        codeView: NumericCodeView,
        code: String,
    ) {
        activity.runOnUiThread { codeView.setCode(code) }
        ShadowLooper.idleMainLooper()
    }

    /** Sends ENTER to the first digit box, which is where [NumericCodeView] hosts its key listener. */
    protected fun pressEnterOnBackupCode() {
        activity.runOnUiThread {
            backupCodeView.getChildAt(0).dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }
        ShadowLooper.idleMainLooper()
    }

    protected fun clickView(view: View) {
        activity.runOnUiThread { view.performClick() }
        ShadowLooper.idleMainLooper()
    }

    /**
     * The fragment reads the toggle at submission time, so flipping it on the live session-data
     * instance takes effect without relaunching the screen.
     */
    protected fun activateEmailOtpToggle() {
        sessionData.featureReleaseToggles =
            listOf(
                ConnectReleaseToggleRecord.releaseToggleFromJson(
                    EMAIL_OTP_VERIFICATION_SLUG,
                    JSONObject().apply { put("active", true) },
                ),
            )
    }

    companion object {
        const val TEST_SESSION_TOKEN: String = "test_session_token_abc"
        const val TEST_USER_NAME: String = "Test User"
        const val TEST_PHONE_NUMBER: String = "+11234567890"
        const val TEST_BACKUP_CODE: String = "123456"

        // Matches the slug ReleaseToggleHelper looks up for the email-OTP feature.
        const val EMAIL_OTP_VERIFICATION_SLUG: String = "email_otp_verification"
    }
}
