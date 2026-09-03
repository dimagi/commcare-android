package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentPersonalidSendEmailOtpBinding
import org.commcare.fragments.extensions.hasLiveView
import org.commcare.fragments.personalId.EmailHelper.maskEmail

/**
 * Screen that sends an email OTP to the user and navigates to the verification screen.
 */
class PersonalIdSendEmailOtpFragment : BasePersonalIdFragment() {
    private lateinit var binding: FragmentPersonalidSendEmailOtpBinding
    private lateinit var email: String
    private var masked: Boolean = true
    private lateinit var workflow: EmailWorkFlow
    private val emailOtpTracker = AttemptTracker()

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
        val args = PersonalIdSendEmailOtpFragmentArgs.fromBundle(requireArguments())
        email = args.email
        masked = args.masked
        workflow = args.workflow
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
            null,
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

    private fun navigateToVerification() {
        val directions =
            PersonalIdSendEmailOtpFragmentDirections
                .actionPersonalidSendEmailOtpToEmailVerification(email, workflow, emailOtpTracker.requestCount)
        binding.root.findNavController().navigate(directions)
    }

    private fun clearError() {
        binding.personalidSendEmailOtpError.visibility = View.GONE
        binding.personalidSendEmailOtpError.text = ""
    }

    private fun showError(message: String) {
        binding.personalidSendEmailOtpError.visibility = View.VISIBLE
        binding.personalidSendEmailOtpError.text = message
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
    }
}
