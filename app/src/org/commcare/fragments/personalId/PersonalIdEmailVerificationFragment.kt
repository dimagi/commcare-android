package org.commcare.fragments.personalId

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import org.commcare.activities.CommCareActivity
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.ConnectConstants
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentPersonalidEmailVerificationBinding
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.views.dialogs.StandardAlertDialog
import java.util.concurrent.TimeUnit
import org.javarosa.core.services.Logger

class PersonalIdEmailVerificationFragment : BasePersonalIdFragment() {
    private lateinit var binding: FragmentPersonalidEmailVerificationBinding
    private lateinit var activity: Activity

    /**
     * Activity-scoped session data populated by upstream PersonalID fragments
     * (phone / biometric / OTP / name / backup-code).
     * Null for the existing user flow.
     */
    private var personalIdSessionData: PersonalIdSessionData? = null

    private lateinit var enteredEmail: String

    /**
     * Launch context for this screen — distinguishes brand-new signup, account recovery,
     * and the "existing user adding email" entry point. Read from a required nav arg.
     */
    private lateinit var workflow: EmailWorkFlow

    private val resendHandler = Handler(Looper.getMainLooper())
    private var otpRequestTime: Long = 0L
    private val resendCooldownMillis = TimeUnit.MINUTES.toMillis(2)
    private var failedOtpAttempts = 0
    private val maxOtpAttempts = 3

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
        personalIdSessionData =
            ViewModelProvider(requireActivity())
                .get(PersonalIdSessionDataViewModel::class.java)
                .personalIdSessionData
        enteredEmail = PersonalIdEmailVerificationFragmentArgs.fromBundle(requireArguments()).email
        workflow = PersonalIdEmailVerificationFragmentArgs.fromBundle(requireArguments()).workflow
    }

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
            getString(R.string.personalid_email_verification_description, enteredEmail)

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

    private fun enableVerifyButton(enabled: Boolean) {
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
            onSuccess = { startResendTimer() },
            onFailure = { failureCode, t ->
                showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t))
            },
        )
    }

    private fun submitOtp() {
        val otp = binding.otpCodeView.codeValue
        if (otp.length != 6) {
            Logger.exception("Invalid email otp", Exception("Invalid email otp length error - ${otp.length}"));
            return
        }
        FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(javaClass.simpleName, null)
        clearError()
        enableVerifyButton(false)

        EmailHelper.verifyEmailOtp(
            activity = requireActivity(),
            email = enteredEmail,
            otp = otp,
            workflow = workflow,
            sessionData = personalIdSessionData,
            onSuccess = { onEmailVerified() },
            onFailure = { failureCode, t ->
                if (!handleCommonSignupFailures(failureCode)) {
                    showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t))
                    failedOtpAttempts++
                    if (failedOtpAttempts >= maxOtpAttempts) {
                        showProceedWithoutEmailDialog()
                    } else if (failureCode.shouldAllowRetry()) {
                        enableVerifyButton(true)
                    }
                }
            },
        )
    }

    private fun onEmailVerified() {
        when (workflow) {
            EmailWorkFlow.EXISTING_USER -> {
                val user = ConnectUserDatabaseUtil.getUser(requireActivity())
                user.email = enteredEmail
                ConnectUserDatabaseUtil.storeUser(requireActivity(), user)
                showEmailAddedSuccessDialog()
            }

            EmailWorkFlow.RECOVERY -> {
                personalIdSessionData!!.email = enteredEmail
                EmailHelper.finalizeRecoveryAndShowSuccess(
                    requireActivity(),
                    personalIdSessionData!!,
                    ::navigateToRecoverySuccess,
                )
            }

            EmailWorkFlow.REGISTRATION -> {
                personalIdSessionData!!.email = enteredEmail
                navigateToPhotoCapture()
            }
        }
    }

    private fun navigateToRecoverySuccess() {
        navigateToMessageDisplay(
            getString(R.string.connect_recovery_success_title),
            getString(R.string.connect_recovery_success_message),
            isCancellable = false,
            phase = ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
            buttonText = R.string.ok,
        )
    }

    private fun showProceedWithoutEmailDialog() {
        val commCareActivity = requireActivity() as CommCareActivity<*>
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_email_otp_failed_title),
                getString(R.string.personalid_email_otp_failed_message),
            )
        dialog.setPositiveButton(getString(R.string.personalid_email_otp_failed_retry)) { _, _ ->
            commCareActivity.dismissAlertDialog()
            failedOtpAttempts = 0
            binding.otpCodeView.clearCode()
            clearError()
            enableVerifyButton(false)
        }
        dialog.setNegativeButton(getString(R.string.personalid_email_otp_failed_skip)) { _, _ ->
            commCareActivity.dismissAlertDialog()
            proceedWithoutEmail()
        }
        commCareActivity.showAlertDialog(dialog)
    }

    /**
     * Confirms to the exiting (already-logged-in) user that the email was saved, then closes
     * the activity once they acknowledge. The dialog is non-cancellable so the user must
     * press OK — the DB write already succeeded by the time we get here.
     */
    private fun showEmailAddedSuccessDialog() {
        val commCareActivity = requireActivity() as CommCareActivity<*>
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_email_added_title),
                getString(R.string.personalid_email_added_message),
            )
        dialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
            commCareActivity.dismissAlertDialog()
            requireActivity().finish()
        }
        commCareActivity.showAlertDialog(dialog)
    }

    private fun proceedWithoutEmail() {
        // No need to null out sessionData.email — only the OTP-verify success path writes it,
        // and that path was not taken on this branch.
        EmailHelper.routeAfterEmailDeclined(
            fragment = this,
            workflow = workflow,
            sessionData = personalIdSessionData,
            onRegistration = { navigateToPhotoCapture() },
            onRecoverySuccess = { navigateToRecoverySuccess() },
        )
    }

    private fun navigateToPhotoCapture() {
        binding.root
            .findNavController()
            .navigate(
                PersonalIdEmailVerificationFragmentDirections
                    .actionPersonalidEmailVerificationToPersonalidPhotoCapture(),
            )
    }

    private fun clearError() {
        binding.personalidEmailVerifyError.visibility = View.GONE
        binding.personalidEmailVerifyError.text = ""
        binding.otpCodeView.setErrorState(false)
    }

    private fun showError(message: String) {
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
    ) {
        val action =
            PersonalIdEmailVerificationFragmentDirections
                .actionPersonalidEmailVerificationToPersonalidMessage(
                    title,
                    message.orEmpty(),
                    phase,
                    getString(buttonText),
                    null,
                ).setIsCancellable(isCancellable)
        binding.root.findNavController().navigate(action)
    }
}
