package org.commcare.fragments.personalId

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

    /**
     * Set once the server has thrown the OTP code away after too many wrong guesses. Even the right
     * OTP code could be rejected, so the user is offered resend without waiting out the countdown.
     */
    private var resendCooldownSkipped = false

    private val resendTimerRunnable =
        object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - otpRequestTime
                val remaining = if (resendCooldownSkipped) 0 else resendCooldownMillis - elapsed
                updateResendButtonState(remaining)
                if (remaining > 0) {
                    resendHandler.postDelayed(this, 1000)
                }
            }
        }

    private fun beginResendCooldown(cooldownMillis: Long = DEFAULT_RESEND_COOLDOWN_MILLIS) {
        resendCooldownSkipped = false
        resendCooldownMillis = cooldownMillis
        otpRequestTime = System.currentTimeMillis()
        resumeResendTimer()
    }

    private fun allowResendNow() {
        resendCooldownSkipped = true
        stopResendTimer()
        updateResendButtonState(0)
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
            resendCooldownSkipped = it.getBoolean(KEY_RESEND_COOLDOWN_SKIPPED)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_OTP_REQUEST_TIME, otpRequestTime)
        outState.putLong(KEY_RESEND_COOLDOWN_MILLIS, resendCooldownMillis)
        outState.putBoolean(KEY_RESEND_COOLDOWN_SKIPPED, resendCooldownSkipped)
    }

    override fun onResume() {
        super.onResume()

        if (resendCooldownSkipped) {
            updateResendButtonState(0)
        } else {
            resumeResendTimer()
        }
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
        binding.otpCodeView.setCodeCompleteListener { _ -> submitOtp() }
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
        if (remaining <= 0) {
            binding.personalidResendCountdownText.visibility = View.GONE
            binding.personalidEmailResendButton.visibility = View.VISIBLE
        } else {
            binding.personalidEmailResendButton.visibility = View.GONE
            binding.personalidResendCountdownText.visibility = View.VISIBLE
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining).toInt()
            binding.personalidResendCountdownText.text =
                getString(
                    R.string.personalid_otp_resend_wait,
                    OtpWaitFormatter.format(requireContext(), seconds),
                )
        }
    }

    private fun requestOtp() {
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
        binding.otpCodeView.clearCode()
        enableVerifyButton(false)
        allowResendNow()
        showError(
            PersonalIdOrConnectApiErrorHandler.handle(
                requireActivity(),
                BaseApiHandler.PersonalIdOrConnectApiErrorCodes.OTP_LIMIT_EXCEEDED_ERROR,
                throwable,
            ),
        )
        if (canSkipEmailVerification()) {
            showProceedWithoutEmailDialog()
        }
    }

    open fun canSkipEmailVerification(): Boolean = false

    open fun onEmailVerified() {}

    private fun showProceedWithoutEmailDialog() {
        val commCareActivity = requireActivity() as CommCareActivity<*>
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_email_otp_failed_title),
                getString(R.string.personalid_email_otp_failed_message),
            )
        dialog.setPositiveButton(getString(R.string.personalid_email_otp_failed_retry)) { _, _ ->
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
        private const val KEY_RESEND_COOLDOWN_SKIPPED = "KEY_RESEND_COOLDOWN_SKIPPED"
    }
}
