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
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.google.android.gms.auth.api.phone.SmsRetriever
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
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

class PersonalIdPhoneVerificationFragment : BasePersonalIdFragment() {
    private var primaryPhone: String? = null
    private var otpRequestTime: DateTime? = null
    private var smsBroadcastReceiver: SMSBroadcastReceiver? = null
    private lateinit var binding: ScreenPersonalidPhoneVerifyBinding
    private val resendTimerHandler = Handler(Looper.getMainLooper())
    private var currentOtpOp: OtpAnalyticsMapper.OtpOp? = null
    private lateinit var otpManager: OtpManager
    private lateinit var personalIdSessionData: PersonalIdSessionData
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        personalIdSessionData =
            ViewModelProvider(requireActivity())[PersonalIdSessionDataViewModel::class.java]
                .personalIdSessionData
        if (savedInstanceState != null) {
            primaryPhone = savedInstanceState.getString(KEY_PHONE)
            lastOtpMethod = savedInstanceState.getString(KEY_LAST_OTP_METHOD)
        } else {
            primaryPhone = personalIdSessionData.phoneNumber
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
                navigateToNameEntry()
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
                if (otpCallback == null) return

                recordFailedVerificationAttempt()
                reportOtpAnalytics(
                    AnalyticsParamValue.OTP_OUTCOME_FAILURE,
                    OtpAnalyticsMapper.reasonFrom(failureCode),
                )

                if (handleCommonSignupFailures(failureCode)) {
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
        if (SMS_METHOD_PERSONAL_ID.equals(personalIdSessionData.smsMethod, ignoreCase = true)) {
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
    }

    private fun setupListeners() {
        binding.connectResendButton.setOnClickListener {
            // Always fallback to Twilio (via PersonalID) if this is the first time the user reattempts to send the OTP.
            val useOtpFallback = personalIdSessionData.otpAttempts == 1
            setupOtpManager(useOtpFallback)
            requestOtp()
        }
        binding.connectPhoneVerifyChange.paintFlags =
            binding.connectPhoneVerifyChange.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.connectPhoneVerifyChange.setOnClickListener { navigateToPhoneEntry() }
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
        otpLimitExceeded = true
        binding.customOtpView.clearCode()
        binding.customOtpView.isEnabled = false
        binding.connectPhoneVerifyButton.isEnabled = false
        val retryAfterSeconds = (throwable as? RateLimitedException)?.retryAfterSeconds
        resendCooldownSeconds = retryAfterSeconds ?: 0
        otpRequestTime = DateTime()
        updateResendButtonState()
        displayOtpError(
            PersonalIdOrConnectApiErrorHandler.handle(
                requireActivity(),
                PersonalIdOrConnectApiErrorCodes.OTP_LIMIT_EXCEEDED_ERROR,
                throwable,
            ),
        )
    }

    private fun clearOtpError() {
        binding.connectPhoneVerifyError.visibility = View.GONE
        binding.customOtpView.setErrorState(false)
    }

    private fun displayOtpError(message: String?) {
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
        requireContext().unregisterReceiver(smsBroadcastReceiver)
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
        outState.putBoolean(KEY_VERIFY_BUTTON_ENABLED, binding.connectPhoneVerifyButton.isEnabled)
        outState.putString(KEY_OTP_REQUEST_TIME_STRING, otpRequestTime.toString())
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
            personalIdSessionData.otpVerificationFailedAttempts =
                personalIdSessionData.otpVerificationFailedAttempts + 1
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
            personalIdSessionData.otpAttempts,
            personalIdSessionData.otpVerificationFailedAttempts,
            OtpAnalyticsMapper.workflowParam(EmailWorkFlow.REGISTRATION),
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
        personalIdSessionData.otpAttempts = personalIdSessionData.otpAttempts + 1
    }

    private fun verifyOtp() {
        FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(
            javaClass.simpleName,
            null,
            PersonalIdWorkflow.CONFIGURATION,
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

        val resendStatusText =
            if (canResend) {
                getString(R.string.connect_verify_phone_resend)
            } else {
                getString(countdownMessage, OtpWaitFormatter.format(requireContext(), secondsRemaining))
            }
        binding.connectPhoneVerifyResend.text = resendStatusText
    }

    private fun navigateToPhoneEntry() {
        Navigation
            .findNavController(binding.connectResendButton)
            .popBackStack(R.id.personalid_phone_fragment, false)
    }

    private fun navigateToNameEntry() {
        val directions = PersonalIdPhoneVerificationFragmentDirections.actionPersonalidOtpPageToPersonalidName()
        Navigation.findNavController(binding.connectResendButton).navigate(directions)
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        val directions =
            PersonalIdPhoneVerificationFragmentDirections
                .actionPersonalidOtpPageToPersonalidMessage(title, message.orEmpty(), phase, getString(buttonText), null)
                .setIsCancellable(isCancellable)
        Navigation.findNavController(binding.root).navigate(directions)
    }

    /**
     * @return true if the PersonalID fallback was explicitly applied, false if it was not
     *         requested or is not allowed for this user. Note that false does not imply Firebase:
     *         the session's own SMS method may already be PersonalID.
     */
    private fun setupOtpManager(useOtpFallback: Boolean): Boolean {
        val authInfo = AuthInfo.TokenAuth(personalIdSessionData.token)

        // The fallback for the OTP uses Twilio (via PersonalID) rather than Firebase.
        if (useOtpFallback && personalIdSessionData.otpFallback) {
            otpManager = OtpManager(requireActivity(), authInfo, personalIdSessionData, otpCallback, SMS_METHOD_PERSONAL_ID)
            lastOtpMethod = SMS_METHOD_PERSONAL_ID
            return true
        }

        // The session's own SMS method may already be PersonalID; anything else means Firebase.
        val method =
            if (SMS_METHOD_PERSONAL_ID.equals(personalIdSessionData.smsMethod, ignoreCase = true)) {
                SMS_METHOD_PERSONAL_ID
            } else {
                SMS_METHOD_FIREBASE
            }
        otpManager = OtpManager(requireActivity(), authInfo, personalIdSessionData, otpCallback, method)
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
