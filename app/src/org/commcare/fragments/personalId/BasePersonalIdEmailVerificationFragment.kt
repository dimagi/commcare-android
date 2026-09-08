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
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentPersonalidEmailVerificationBinding
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdRecoveryCompleter
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
    private val resendCooldownMillis = TimeUnit.MINUTES.toMillis(2)
    protected var failedOtpAttempts = 0
    protected val maxOtpAttempts = 3

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

    private fun startResendTimer() {
        otpRequestTime = System.currentTimeMillis()
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
        startResendTimer()

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
                getString(R.string.connect_verify_phone_resend_wait, seconds)
        }
    }

    private fun requestOtp() {
        otpRequestTime = System.currentTimeMillis()
        binding.otpCodeView.clearCode()
        clearError()

        EmailHelper.sendEmailOtp(
            activity = requireActivity(),
            email = enteredEmail,
            workflow = workflow,
            sessionData = personalIdSessionData,
            tracker = emailOtpTracker,
            onSuccess = {
                if (!hasLiveView()) return@sendEmailOtp
                startResendTimer()
            },
            onFailure = { failureCode, t ->
                if (!hasLiveView()) return@sendEmailOtp
                showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t))
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
        t: Throwable?
    ) {
        if (!handleCommonSignupFailures(failureCode)) {
            showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t))
            failedOtpAttempts++
            if (failedOtpAttempts >= maxOtpAttempts) {
                onMaxingEmailVerificationAttempts()
            } else if (failureCode.shouldAllowRetry()) {
                enableVerifyButton(true)
            }
        }
    }

    protected open fun onMaxingEmailVerificationAttempts() {
        if (canSkipEmailVerification()) {
            showProceedWithoutEmailDialog()
        } else {
            showError(getString(R.string.personalid_email_otp_max_attempts_reached))
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
            failedOtpAttempts = 0
            binding.otpCodeView.clearCode()
            clearError()
            enableVerifyButton(false)
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
    ) { /* no default message destination */ }
}
