package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentPersonalidSendOtpBinding
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.fragments.personalId.EmailHelper.maskEmail
import org.commcare.utils.CommCareAttemptCounter

/**
 * Base fragment for screens that send an email OTP to the user
 */
abstract class BasePersonalIdSendEmailOtpFragment : BasePersonalIdFragment() {
    protected lateinit var binding: FragmentPersonalidSendOtpBinding
    protected lateinit var email: String
    protected var masked: Boolean = true
    protected lateinit var workflow: EmailWorkFlow
    protected val emailOtpTracker = CommCareAttemptCounter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initArguments()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPersonalidSendOtpBinding.inflate(inflater, container, false)
        setUpView()
        return binding.root
    }

    private fun initArguments() {
        email = requireArguments().getString("email")!!
        masked = requireArguments().getBoolean("masked", true)
        workflow = requireArguments().getSerializable("workflow") as EmailWorkFlow
    }

    open fun setUpView() {
        setAppBarTitle()
        binding.sendOtpAddress.text = if (masked) maskEmail(email) else email
        binding.sendOtpButton.setOnClickListener { sendCode() }
        clearError()
    }

    open fun setAppBarTitle() {
        setTitle(R.string.personalid_send_email_otp_title)
    }

    private fun sendCode() {
        binding.sendOtpButton.isEnabled = false
        clearError()
        EmailHelper.sendEmailOtp(
            activity = requireActivity(),
            email = emailForApiCall(),
            workflow = workflow,
            sessionData = getSessionData(),
            tracker = emailOtpTracker,
            onSuccess = {
                if (!hasLiveView()) return@sendEmailOtp
                navigateToVerification()
            },
            onFailure = { failureCode, t ->
                if (!hasLiveView()) return@sendEmailOtp
                showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t))
                binding.sendOtpButton.isEnabled = true
            },
        )
    }

    protected fun clearError() {
        binding.sendOtpError.visibility = View.GONE
        binding.sendOtpError.text = ""
    }

    protected fun showError(message: String) {
        binding.sendOtpError.visibility = View.VISIBLE
        binding.sendOtpError.text = message
    }

    abstract fun getSessionData(): PersonalIdSessionData?

    abstract fun navigateToVerification()

    open fun emailForApiCall(): String? = email

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
    }
}
