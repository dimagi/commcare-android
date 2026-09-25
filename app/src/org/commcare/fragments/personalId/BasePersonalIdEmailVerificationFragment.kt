package org.commcare.fragments.personalId

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.StringRes
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.RateLimitedException
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentPersonalidEmailVerificationBinding
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.OtpWaitFormatter
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.Logger
import java.util.concurrent.TimeUnit

abstract class BasePersonalIdEmailVerificationFragment : BasePersonalIdFragment() {
    protected lateinit var binding: FragmentPersonalidEmailVerificationBinding
    private lateinit var activity: Activity
    private lateinit var emailOtpTracker: AttemptTracker

    private var personalIdSessionData: PersonalIdSessionData? = null

    protected lateinit var enteredEmail: String

    protected lateinit var workflow: EmailWorkFlow

    private val resendHandler = Handler(Looper.getMainLooper())
    private var otpRequestTime: Long = 0L
    private var resendCooldownMillis = DEFAULT_RESEND_COOLDOWN_MILLIS

    private var otpLimitExceeded = false

    private val resendTimerRunnable =
        object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - otpRequestTime
                val remaining = resendCooldownMillis - elapsed
                updateResendButtonState(remaining)
                if (remaining > 0) {
                    resendHandler.postDelayed(this, 1000)
                }
            }
        }

    private fun beginResendCooldown(cooldownMillis: Long = DEFAULT_RESEND_COOLDOWN_MILLIS) {
        resendCooldownMillis = cooldownMillis
        otpRequestTime = System.currentTimeMillis()
        resumeResendTimer()
    }

    private fun resumeResendTimer() {
        stopResendTimer()
        resendHandler.postDelayed(resendTimerRunnable, 100)
    }

    private fun stopResendTimer() {
        resendHandler.removeCallbacks(resendTimerRunnable)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        personalIdSessionData = getSessionData()
        enteredEmail = resolveEmail()
        workflow = resolveWorkflow()
        emailOtpTracker = AttemptTracker(initialRequestCount = resolveEmailOtpRequestCount())
        savedInstanceState?.let {
            otpRequestTime = it.getLong(KEY_OTP_REQUEST_TIME)
            resendCooldownMillis = it.getLong(KEY_RESEND_COOLDOWN_MILLIS, DEFAULT_RESEND_COOLDOWN_MILLIS)
            otpLimitExceeded = it.getBoolean(KEY_OTP_LIMIT_EXCEEDED)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_OTP_REQUEST_TIME, otpRequestTime)
        outState.putLong(KEY_RESEND_COOLDOWN_MILLIS, resendCooldownMillis)
        outState.putBoolean(KEY_OTP_LIMIT_EXCEEDED, otpLimitExceeded)
    }

    override fun onResume() {
        super.onResume()
        resumeResendTimer()
    }

    override fun onPause() {
        super.onPause()
        stopResendTimer()
    }

    abstract fun resolveEmail(): String

    abstract fun displayEmail(): String

    abstract fun resolveWorkflow(): EmailWorkFlow

    abstract fun resolveEmailOtpRequestCount(): Int

    protected open fun getSessionData(): PersonalIdSessionData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPersonalidEmailVerificationBinding.inflate(inflater, container, false)
        activity = requireActivity()
        activity.setTitle(R.string.personalid_email_verification_appbar_title)
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        binding.emailVerificationDescription.text =
            getString(R.string.personalid_email_verification_description, displayEmail())

        binding.otpCodeView.setOnCodeChangedListener { code -> enableVerifyButton(code.length == 6) }
        binding.otpCodeView.setOnEnterKeyPressedListener { submitOtp() }
        binding.personalidEmailVerifyButton.setOnClickListener { submitOtp() }
        binding.personalidEmailResendButton.setOnClickListener { requestOtp() }

        enableVerifyButton(false)
        if (savedInstanceState == null) {
            beginResendCooldown()
        }

        setupKeyboardScrollListener(binding.personalidEmailVerificationScrollView)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopResendTimer()
        destroyKeyboardScrollListener(binding.personalidEmailVerificationScrollView)
    }

    protected fun enableVerifyButton(enabled: Boolean) {
        binding.personalidEmailVerifyButton.isEnabled = enabled
    }

    private fun updateResendButtonState(remaining: Long) {
        @StringRes val resendButtonLabel =
            if (otpLimitExceeded) {
                R.string.personalid_otp_request_code
            } else {
                R.string.connect_verify_phone_resend_code
            }

        @StringRes val countdownMessage =
            if (otpLimitExceeded) {
                R.string.personalid_otp_request_new_code_wait
            } else {
                R.string.personalid_otp_resend_wait
            }

        binding.personalidEmailResendButton.setText(resendButtonLabel)

        if (remaining <= 0) {
            binding.personalidResendCountdownText.visibility = View.GONE
            binding.personalidEmailResendButton.visibility = View.VISIBLE
        } else {
            binding.personalidEmailResendButton.visibility = View.GONE
            binding.personalidResendCountdownText.visibility = View.VISIBLE
            val secondsRemaining = TimeUnit.MILLISECONDS.toSeconds(remaining).toInt()
            binding.personalidResendCountdownText.text =
                getString(countdownMessage, OtpWaitFormatter.format(requireContext(), secondsRemaining))
        }
    }

    private fun requestOtp() {
        otpLimitExceeded = false
        binding.otpCodeView.isEnabled = true
        beginResendCooldown()
        binding.otpCodeView.clearCode()
        clearError()

        EmailHelper.sendEmailOtp(
            activity = requireActivity(),
            email = emailForApiCall(),
            workflow = workflow,
            sessionData = personalIdSessionData,
            tracker = emailOtpTracker,
            onSuccess = {
                if (!hasLiveView()) return@sendEmailOtp
                beginResendCooldown()
            },
            onFailure = { failureCode, throwable ->
                if (!hasLiveView()) return@sendEmailOtp
                showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, throwable))
                (throwable as? RateLimitedException)?.retryAfterSeconds?.let { retryAfterSeconds ->
                    beginResendCooldown(TimeUnit.SECONDS.toMillis(retryAfterSeconds.toLong()))
                }
            },
        )
    }

    private fun submitOtp() {
        val otp = binding.otpCodeView.codeValue
        if (otp.length != 6) {
            Logger.exception("Invalid email otp", Exception("Invalid email otp length error - ${otp.length}"))
            return
        }
        FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(
            javaClass.simpleName,
            null,
            PersonalIdWorkflow.fromEmailWorkFlow(workflow),
        )
        clearError()
        enableVerifyButton(false)

        doVerifyOtpRequest(otp)
    }

    protected open fun doVerifyOtpRequest(otp: String) {
        EmailHelper.verifyEmailOtp(
            activity = requireActivity(),
            email = enteredEmail,
            otp = otp,
            workflow = workflow,
            sessionData = personalIdSessionData,
            tracker = emailOtpTracker,
            onSuccess = {
                if (!hasLiveView()) return@verifyEmailOtp
                onEmailVerified()
            },
            onFailure = { failureCode, t ->
                if (!hasLiveView()) return@verifyEmailOtp
                onEmailVerificationFailure(failureCode, t)
            },
        )
    }

    protected fun onEmailVerificationFailure(
        failureCode: BaseApiHandler.PersonalIdOrConnectApiErrorCodes,
        throwable: Throwable?,
    ) {
        if (handleCommonSignupFailures(failureCode)) return

        if (failureCode == BaseApiHandler.PersonalIdOrConnectApiErrorCodes.OTP_LIMIT_EXCEEDED_ERROR) {
            onOtpLimitExceeded(throwable)
        } else {
            showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, throwable))
            if (failureCode.shouldAllowRetry()) {
                enableVerifyButton(true)
            }
        }
    }

    private fun onOtpLimitExceeded(throwable: Throwable?) {
        otpLimitExceeded = true
        binding.otpCodeView.clearCode()
        binding.otpCodeView.isEnabled = false
        enableVerifyButton(false)
        val waitSeconds = (throwable as? RateLimitedException)?.retryAfterSeconds ?: 0
        val waitMillis = TimeUnit.SECONDS.toMillis(waitSeconds.toLong())
        beginResendCooldown(waitMillis)
        showError(
            PersonalIdOrConnectApiErrorHandler.handle(
                requireActivity(),
                BaseApiHandler.PersonalIdOrConnectApiErrorCodes.OTP_LIMIT_EXCEEDED_ERROR,
                throwable,
            ),
        )
        if (canSkipEmailVerification()) {
            showProceedWithoutEmailDialog(waitSeconds)
        }
    }

    open fun canSkipEmailVerification(): Boolean = false

    open fun onEmailVerified() {}

    private fun showProceedWithoutEmailDialog(waitSeconds: Int) {
        val commCareActivity = requireActivity() as CommCareActivity<*>
        val waitText = OtpWaitFormatter.format(requireContext(), waitSeconds)
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_email_otp_failed_title),
                getString(R.string.personalid_email_otp_failed_message, waitText),
            )
        dialog.setPositiveButton(getString(R.string.personalid_email_otp_failed_wait)) { _, _ ->
            FirebaseAnalyticsUtil.reportUserPromptEvent(
                AnalyticsParamValue.USER_PROMPT_TYPE_EMAIL,
                AnalyticsParamValue.USER_PROMPT_ACTION_RETRY,
                AnalyticsParamValue.USER_PROMPT_INFO_EMAIL_VERIFICATION_FAILURE_RETRY,
            )
            commCareActivity.dismissAlertDialog()
        }
        dialog.setNegativeButton(getString(R.string.personalid_email_otp_failed_skip)) { _, _ ->
            FirebaseAnalyticsUtil.reportUserPromptEvent(
                AnalyticsParamValue.USER_PROMPT_TYPE_EMAIL,
                AnalyticsParamValue.USER_PROMPT_ACTION_PROCEED_WITHOUT_EMAIL,
                AnalyticsParamValue.USER_PROMPT_INFO_EMAIL_VERIFICATION_FAILURE_RETRY,
            )
            commCareActivity.dismissAlertDialog()
            proceedWithoutEmail()
        }
        commCareActivity.showAlertDialog(dialog)
    }

    protected open fun emailForApiCall(): String? = enteredEmail

    protected open fun proceedWithoutEmail() {
        // Override in subclasses to handle proceeding without email verification
    }

    protected fun clearError() {
        binding.personalidEmailVerifyError.visibility = View.GONE
        binding.personalidEmailVerifyError.text = ""
        binding.otpCodeView.setErrorState(false)
    }

    protected fun showError(message: String) {
        binding.personalidEmailVerifyError.visibility = View.VISIBLE
        binding.personalidEmailVerifyError.text = message
        binding.otpCodeView.setErrorState(true)
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ): Unit = throw IllegalStateException("navigateToMessageDisplay should not have a call path in this fragment")

    companion object {
        private val DEFAULT_RESEND_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(2)
        private const val KEY_OTP_REQUEST_TIME = "KEY_OTP_REQUEST_TIME"
        private const val KEY_RESEND_COOLDOWN_MILLIS = "KEY_RESEND_COOLDOWN_MILLIS"
        private const val KEY_OTP_LIMIT_EXCEEDED = "KEY_OTP_LIMIT_EXCEEDED"
    }
}
