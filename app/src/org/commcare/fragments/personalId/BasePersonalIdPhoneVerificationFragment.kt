package org.commcare.fragments.personalId

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import com.google.android.gms.auth.api.phone.SmsRetriever
import org.commcare.connect.SMSBroadcastReceiver
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.RateLimitedException
import org.commcare.core.network.AuthInfo
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ScreenPersonalidPhoneVerifyBinding
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.util.LogTypes
import org.commcare.utils.AttemptCounter
import org.commcare.utils.KeyboardHelper
import org.commcare.utils.OtpAnalyticsMapper
import org.commcare.utils.OtpErrorType
import org.commcare.utils.OtpManager
import org.commcare.utils.OtpManager.SMS_METHOD_FIREBASE
import org.commcare.utils.OtpManager.SMS_METHOD_PERSONAL_ID
import org.commcare.utils.OtpVerificationCallback
import org.commcare.utils.OtpWaitFormatter
import org.javarosa.core.services.Logger
import org.joda.time.DateTime

/**
 * Phone OTP entry screen shared by signup and Manage Profile. Owns the UI, the resend timer, SMS
 * autofill, and the OTP method strategy: start with [defaultSmsMethod], switch to PersonalID SMS on
 * a non-recoverable Firebase error or on the first resend, when [isFallbackAllowed].
 */
abstract class BasePersonalIdPhoneVerificationFragment : BasePersonalIdFragment() {
    private var primaryPhone: String? = null
    private var otpRequestTime: DateTime? = null
    private var smsBroadcastReceiver: SMSBroadcastReceiver? = null
    protected lateinit var binding: ScreenPersonalidPhoneVerifyBinding
    private val resendTimerHandler = Handler(Looper.getMainLooper())
    private var currentOtpOp: OtpAnalyticsMapper.OtpOp? = null
    private lateinit var otpManager: OtpManager
    private var otpCallback: OtpVerificationCallback? = null
    private lateinit var smsConsentLauncher: ActivityResultLauncher<Intent>
    private var lastOtpMethod: String? = null
    private var otpLimitExceeded = false
    private var resendCooldownSeconds = DEFAULT_RESEND_COOLDOWN_SECONDS

    private val resendTimerRunnable =
        object : Runnable {
            override fun run() {
                updateResendButtonState()
                resendTimerHandler.postDelayed(this, 100)
            }
        }

    /** Phone number the OTP is sent to, in E.164 format. */
    protected abstract fun getPhoneNumber(): String?

    /** Credentials for the PersonalID OTP calls. */
    protected abstract fun buildAuthInfo(): AuthInfo

    /** SMS method to start with; anything other than PersonalID means Firebase. */
    protected abstract fun defaultSmsMethod(): String?

    protected abstract fun isFallbackAllowed(): Boolean

    protected abstract fun attemptCounter(): AttemptCounter

    protected abstract fun analyticsWorkflow(): EmailWorkFlow

    protected abstract fun continueClickedWorkflow(): PersonalIdWorkflow

    protected abstract fun onOtpVerified()

    /** @return true when the failure was fully handled and no inline error should be shown. */
    protected open fun handleApiFailure(failureCode: PersonalIdOrConnectApiErrorCodes): Boolean = false

    protected open fun showChangeNumberLink(): Boolean = true

    protected open fun onChangeNumberClicked() = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            primaryPhone = savedInstanceState.getString(KEY_PHONE)
            lastOtpMethod = savedInstanceState.getString(KEY_LAST_OTP_METHOD)
        } else {
            primaryPhone = getPhoneNumber()
        }
        smsConsentLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val message = result.data!!.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
                    binding.customOtpView.setCode(extractOtp(message)) // Autofill OTP
                }
            }
        otpCallback = createOtpCallback()
        // The last OTP method may be Twilio (via PersonalID) after restoring this fragment.
        setupOtpManager(SMS_METHOD_PERSONAL_ID.equals(lastOtpMethod, ignoreCase = true))
    }

    private fun createOtpCallback() =
        object : OtpVerificationCallback {
            override fun onCodeSent(verificationId: String?) {
                if (otpCallback == null) return
                reportOtpAnalytics(AnalyticsParamValue.OTP_OUTCOME_SUCCESS, null)
                Toast.makeText(requireContext(), getString(R.string.connect_otp_sent), Toast.LENGTH_SHORT).show()
            }

            override fun onCodeVerified(code: String?) {
                if (otpCallback == null) return
                // No analytics emission here: this is Firebase's local auto-verification step.
                // Verify success is recorded from the server-confirmed onSuccess callback so
                // we don't double-count, and verify failure is recorded from onFailure /
                // onPersonalIdApiFailure.
                Toast.makeText(requireContext(), getString(R.string.connect_otp_verified), Toast.LENGTH_SHORT).show()
            }

            override fun onSuccess() {
                if (otpCallback == null) return
                reportOtpAnalytics(AnalyticsParamValue.OTP_OUTCOME_SUCCESS, null)
                onOtpVerified()
            }

            override fun onFailure(
                errorType: OtpErrorType,
                errorMessage: String?,
            ) {
                recordFailedVerificationAttempt()
                reportOtpAnalytics(
                    AnalyticsParamValue.OTP_OUTCOME_FAILURE,
                    OtpAnalyticsMapper.reasonFrom(errorType),
                )

                if (otpCallback == null) return

                // Auto-switch from Firebase to PersonalId for non-recoverable errors
                if (shouldAutoSwitchToPersonalIdAuth(errorType) && setupOtpManager(true)) {
                    Logger.log(
                        LogTypes.TYPE_MAINTENANCE,
                        "Auto-switching from Firebase to PersonalId auth due to error: $errorType",
                    )
                    requestOtp()
                    return
                }

                if (errorType == OtpErrorType.SESSION_EXPIRED) {
                    showCodeNoLongerValid(getString(R.string.personalid_otp_session_expired), 0)
                    return
                }

                val userMessage =
                    when (errorType) {
                        OtpErrorType.INVALID_CREDENTIAL -> {
                            getString(R.string.personalid_incorrect_otp)
                        }

                        OtpErrorType.TOO_MANY_REQUESTS -> {
                            getString(R.string.personalid_too_many_otp_attempts)
                        }

                        OtpErrorType.MISSING_ACTIVITY -> {
                            getString(R.string.personalid_otp_verification_failed_generic)
                        }

                        OtpErrorType.VERIFICATION_FAILED -> {
                            getString(R.string.personalid_otp_verification_failed)
                        }

                        else -> {
                            getString(R.string.personalid_otp_verification_failed_generic) +
                                (errorMessage ?: "Unknown error")
                        }
                    }
                displayOtpError(userMessage)
                binding.connectPhoneVerifyButton.isEnabled = false
            }

            override fun onPersonalIdApiFailure(
                failureCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                recordFailedVerificationAttempt()
                reportOtpAnalytics(
                    AnalyticsParamValue.OTP_OUTCOME_FAILURE,
                    OtpAnalyticsMapper.reasonFrom(failureCode),
                )
                if (otpCallback == null) return

                if (handleApiFailure(failureCode)) {
                    return
                }
                if (failureCode == PersonalIdOrConnectApiErrorCodes.OTP_LIMIT_EXCEEDED_ERROR) {
                    onOtpLimitExceeded(t)
                    return
                }
                if (t is RateLimitedException) {
                    holdResendForServerWait(t)
                }
                var error = PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t)
                if (failureCode == PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR) {
                    error = getString(R.string.personalid_incorrect_otp)
                }
                displayOtpError(error)
                binding.connectPhoneVerifyButton.isEnabled = false
            }
        }

    /**
     * Determines if we should auto-switch from Firebase to PersonalId auth based on the error type.
     * Only switches from Firebase to PersonalId, never the reverse.
     */
    private fun shouldAutoSwitchToPersonalIdAuth(errorType: OtpErrorType): Boolean {
        if (SMS_METHOD_PERSONAL_ID.equals(defaultSmsMethod(), ignoreCase = true)) {
            return false
        }
        return errorType.isNonRecoverable
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ScreenPersonalidPhoneVerifyBinding.inflate(inflater, container, false)

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            setupInitialState()
        }

        setupListeners()
        val childCount = binding.customOtpView.childCount
        if (childCount > 0) {
            setUpEnterKeyAction(binding.customOtpView.getChildAt(childCount - 1) as EditText)
        }
        updateVerificationMessage()
        requireActivity().setTitle(R.string.connect_verify_phone_title)

        return binding.root
    }

    private fun setupInitialState() {
        binding.connectPhoneVerifyButton.isEnabled = false
        requestOtp()
    }

    private fun restoreState(savedInstanceState: Bundle) {
        val verifyButtonEnabled = savedInstanceState.getBoolean(KEY_VERIFY_BUTTON_ENABLED)
        val otpRequestTimeString = savedInstanceState.getString(KEY_OTP_REQUEST_TIME_STRING)
        otpLimitExceeded = savedInstanceState.getBoolean(KEY_OTP_LIMIT_EXCEEDED)
        resendCooldownSeconds =
            savedInstanceState.getInt(KEY_RESEND_COOLDOWN_SECONDS, DEFAULT_RESEND_COOLDOWN_SECONDS)
        if (otpRequestTimeString != null) {
            otpRequestTime = DateTime.parse(otpRequestTimeString)
        }
        binding.connectPhoneVerifyButton.isEnabled = verifyButtonEnabled
        binding.customOtpView.isEnabled = !otpLimitExceeded
    }

    private fun setupListeners() {
        binding.connectResendButton.setOnClickListener {
            // Always fallback to Twilio (via PersonalID) if this is the first time the user reattempts to send the OTP.
            val useOtpFallback = attemptCounter().requestCount == 1
            setupOtpManager(useOtpFallback)
            requestOtp()
        }
        if (showChangeNumberLink()) {
            binding.connectPhoneVerifyChange.paintFlags =
                binding.connectPhoneVerifyChange.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            binding.connectPhoneVerifyChange.setOnClickListener { onChangeNumberClicked() }
        } else {
            binding.connectPhoneVerifyChange.visibility = View.GONE
        }
        binding.connectPhoneVerifyButton.setOnClickListener { verifyOtp() }
        binding.customOtpView.setOnCodeChangedListener { otp ->
            clearOtpError()
            toggleVerifyButton(otp)
        }
    }

    private fun toggleVerifyButton(otp: String) {
        binding.connectPhoneVerifyButton.isEnabled = otp.length == 6
    }

    /**
     * Holds the resend button for the wait a rate-limited request reported, so it does not come
     * back before the server will accept another send.
     */
    private fun holdResendForServerWait(rateLimitedException: RateLimitedException) {
        if (currentOtpOp != OtpAnalyticsMapper.OtpOp.REQUEST_PHONE) {
            return
        }

        val retryAfterSeconds = rateLimitedException.retryAfterSeconds ?: return

        resendCooldownSeconds = retryAfterSeconds
        updateResendButtonState()
    }

    private fun onOtpLimitExceeded(throwable: Throwable?) {
        val retryAfterSeconds = (throwable as? RateLimitedException)?.retryAfterSeconds
        val limitExceededMessage =
            PersonalIdOrConnectApiErrorHandler.handle(
                requireActivity(),
                PersonalIdOrConnectApiErrorCodes.OTP_LIMIT_EXCEEDED_ERROR,
                throwable,
            )
        showCodeNoLongerValid(limitExceededMessage, retryAfterSeconds ?: 0)
    }

    private fun showCodeNoLongerValid(
        message: String?,
        retryAfterSeconds: Int,
    ) {
        otpLimitExceeded = true
        binding.customOtpView.clearCode()
        binding.customOtpView.isEnabled = false
        binding.connectPhoneVerifyButton.isEnabled = false
        resendCooldownSeconds = retryAfterSeconds
        otpRequestTime = DateTime()
        updateResendButtonState()
        displayOtpError(message)
    }

    private fun clearOtpError() {
        binding.connectPhoneVerifyError.visibility = View.GONE
        binding.customOtpView.setErrorState(false)
    }

    protected fun displayOtpError(message: String?) {
        if (!message.isNullOrEmpty()) {
            binding.connectPhoneVerifyError.visibility = View.VISIBLE
            binding.connectPhoneVerifyError.text = message
            binding.customOtpView.setErrorState(true)
        }
    }

    override fun onStart() {
        super.onStart()
        startSmsUserConsent()
        registerSmsReceiver()
    }

    override fun onResume() {
        super.onResume()
        startResendTimer()
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.customOtpView)
    }

    private fun startSmsUserConsent() {
        SmsRetriever.getClient(requireContext()).startSmsUserConsent(null) // null = any sender
    }

    private fun registerSmsReceiver() {
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        smsBroadcastReceiver = SMSBroadcastReceiver(smsConsentLauncher)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(smsBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            requireActivity().registerReceiver(smsBroadcastReceiver, filter)
        }
    }

    private fun extractOtp(message: String?): String {
        val match = message?.let { OTP_PATTERN.find(it) }
        if (match != null) {
            return match.value
        }
        Logger.log(LogTypes.TYPE_EXCEPTION, "OTP pattern dose't match")
        return ""
    }

    override fun onStop() {
        super.onStop()
        smsBroadcastReceiver?.let {
            try {
                requireActivity().unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                Logger.exception("SMS receiver was not registered", e)
            }
        }
        smsBroadcastReceiver = null
    }

    override fun onPause() {
        super.onPause()
        stopResendTimer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        otpCallback = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PHONE, primaryPhone)
        outState.putString(KEY_LAST_OTP_METHOD, lastOtpMethod)
        if (::binding.isInitialized) {
            outState.putBoolean(KEY_VERIFY_BUTTON_ENABLED, binding.connectPhoneVerifyButton.isEnabled)
        }
        outState.putString(KEY_OTP_REQUEST_TIME_STRING, otpRequestTime?.toString())
        outState.putBoolean(KEY_OTP_LIMIT_EXCEEDED, otpLimitExceeded)
        outState.putInt(KEY_RESEND_COOLDOWN_SECONDS, resendCooldownSeconds)
    }

    private fun updateVerificationMessage() {
        val phone = primaryPhone
        if (phone != null && phone.length >= 4) {
            val lastFourDigits = phone.substring(phone.length - 4)
            binding.connectPhoneVerifyLabel.text = getString(R.string.connect_verify_phone_label, lastFourDigits)
        }
    }

    private fun recordFailedVerificationAttempt() {
        if (currentOtpOp == OtpAnalyticsMapper.OtpOp.VERIFY_PHONE) {
            attemptCounter().recordFailedAttempt()
        }
    }

    private fun reportOtpAnalytics(
        outcome: String,
        reason: String?,
    ) {
        val method = OtpAnalyticsMapper.methodFromSmsMethod(lastOtpMethod)
        FirebaseAnalyticsUtil.reportOtpEvent(
            currentOtpOp,
            outcome,
            method,
            reason,
            attemptCounter().requestCount,
            attemptCounter().failedAttempts,
            OtpAnalyticsMapper.workflowParam(analyticsWorkflow()),
        )
    }

    private fun requestOtp() {
        clearOtpError()
        otpLimitExceeded = false
        binding.customOtpView.isEnabled = true
        resendCooldownSeconds = DEFAULT_RESEND_COOLDOWN_SECONDS
        otpRequestTime = DateTime()
        currentOtpOp = OtpAnalyticsMapper.OtpOp.REQUEST_PHONE
        otpManager.requestOtp(primaryPhone)
        attemptCounter().recordRequest()
    }

    private fun verifyOtp() {
        FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(
            javaClass.simpleName,
            null,
            continueClickedWorkflow(),
        )
        binding.connectPhoneVerifyButton.isEnabled = false
        clearOtpError()
        val otpCode = binding.customOtpView.codeValue

        if (otpCode.length != 6) {
            Toast.makeText(requireContext(), getString(R.string.connect_enter_otp), Toast.LENGTH_SHORT).show()
        } else {
            currentOtpOp = OtpAnalyticsMapper.OtpOp.VERIFY_PHONE
            otpManager.verifyOtp(otpCode)
        }
    }

    private fun startResendTimer() {
        // Settle the button before the first tick, so a restored screen does not briefly show the
        // state it had before the code ran out of attempts.
        updateResendButtonState()
        resendTimerHandler.postDelayed(resendTimerRunnable, 100)
    }

    private fun stopResendTimer() {
        resendTimerHandler.removeCallbacks(resendTimerRunnable)
    }

    private fun updateResendButtonState() {
        var canResend = true
        var secondsRemaining = 0

        val requestTime = otpRequestTime
        if (requestTime != null) {
            val elapsedMillis = DateTime().millis - requestTime.millis
            val remainingSeconds = resendCooldownSeconds - (elapsedMillis / 1000)
            if (remainingSeconds > 0) {
                canResend = false
                secondsRemaining = remainingSeconds.toInt()
            }
        }

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

        binding.connectResendButton.setText(resendButtonLabel)
        binding.connectResendButton.visibility = if (canResend) View.VISIBLE else View.GONE
        binding.connectPhoneVerifyResend.visibility =
            if (otpLimitExceeded && canResend) View.GONE else View.VISIBLE

        val resendStatusText =
            if (canResend) {
                getString(R.string.connect_verify_phone_resend)
            } else {
                getString(countdownMessage, OtpWaitFormatter.format(requireContext(), secondsRemaining))
            }
        binding.connectPhoneVerifyResend.text = resendStatusText
    }

    /**
     * @return true if the PersonalID fallback was explicitly applied, false if it was not
     *         requested or is not allowed. False does not imply Firebase: [defaultSmsMethod] may
     *         already be PersonalID.
     */
    private fun setupOtpManager(useOtpFallback: Boolean): Boolean {
        val authInfo = buildAuthInfo()

        // The fallback for the OTP uses Twilio (via PersonalID) rather than Firebase.
        if (useOtpFallback && isFallbackAllowed()) {
            otpManager = OtpManager(requireActivity(), authInfo, otpCallback, SMS_METHOD_PERSONAL_ID)
            lastOtpMethod = SMS_METHOD_PERSONAL_ID
            return true
        }

        // The default SMS method may already be PersonalID; anything else means Firebase.
        val method =
            if (SMS_METHOD_PERSONAL_ID.equals(defaultSmsMethod(), ignoreCase = true)) {
                SMS_METHOD_PERSONAL_ID
            } else {
                SMS_METHOD_FIREBASE
            }
        otpManager = OtpManager(requireActivity(), authInfo, otpCallback, method)
        lastOtpMethod = method
        return false
    }

    override fun keyboardEnterPressed() {
        if (binding.connectPhoneVerifyButton.isEnabled) {
            verifyOtp()
        } else {
            KeyboardHelper.hideVirtualKeyboard(requireActivity())
        }
    }

    companion object {
        private const val KEY_PHONE = "KEY_PHONE"
        private const val KEY_LAST_OTP_METHOD = "KEY_LAST_OTP_METHOD"
        private const val KEY_VERIFY_BUTTON_ENABLED = "KEY_VERIFY_BUTTON_ENABLED"
        private const val KEY_OTP_REQUEST_TIME_STRING = "KEY_OTP_REQUEST_TIME_STRING"
        private const val KEY_OTP_LIMIT_EXCEEDED = "KEY_OTP_LIMIT_EXCEEDED"
        private const val KEY_RESEND_COOLDOWN_SECONDS = "KEY_RESEND_COOLDOWN_SECONDS"
        private const val DEFAULT_RESEND_COOLDOWN_SECONDS = 120
        private val OTP_PATTERN = Regex("\\b\\d{6}\\b")
    }
}
