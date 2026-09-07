package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentPersonalidSendEmailOtpBinding
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.fragments.personalId.EmailHelper.maskEmail

/**
 * Base fragment for screens that send an email OTP to the user
 */
abstract class BasePersonalIdSendEmailOtpFragment : BasePersonalIdFragment() {
    protected lateinit var binding: FragmentPersonalidSendEmailOtpBinding
    protected lateinit var email: String
    protected var masked: Boolean = true
    protected lateinit var workflow: EmailWorkFlow
    protected val emailOtpTracker = AttemptTracker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initArguments()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPersonalidSendEmailOtpBinding.inflate(inflater, container, false)
        setUpView()
        return binding.root
    }

    private fun initArguments() {
        email = requireArguments().getString("email")!!
        masked = requireArguments().getBoolean("masked", true)
        workflow = requireArguments().getSerializable("workflow") as EmailWorkFlow
    }

    private fun setUpView() {
        requireActivity().setTitle(R.string.personalid_send_email_otp_title)
        binding.personalidSendEmailOtpAddress.text = if (masked) maskEmail(email) else email
        binding.personalidSendEmailOtpButton.setOnClickListener { sendCode() }
        clearError()
    }

    private fun sendCode() {
        binding.personalidSendEmailOtpButton.isEnabled = false
        clearError()
        EmailHelper.sendEmailOtp(
            activity = requireActivity(),
            email = email,
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
                binding.personalidSendEmailOtpButton.isEnabled = true
            },
        )
    }

    protected fun clearError() {
        binding.personalidSendEmailOtpError.visibility = View.GONE
        binding.personalidSendEmailOtpError.text = ""
    }

    protected fun showError(message: String) {
        binding.personalidSendEmailOtpError.visibility = View.VISIBLE
        binding.personalidSendEmailOtpError.text = message
    }

    abstract fun getSessionData(): PersonalIdSessionData?

    abstract fun navigateToVerification()

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
    }
}
