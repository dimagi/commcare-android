package org.commcare.fragments.personalId

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentPersonalidEmailBinding
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdRecoveryCompleter
import org.commcare.utils.KeyboardHelper
import org.commcare.utils.StringUtils
import org.commcare.views.dialogs.StandardAlertDialog

class PersonalIdEmailFragment : BasePersonalIdFragment() {
    private lateinit var binding: FragmentPersonalidEmailBinding
    private lateinit var personalIdSessionData: PersonalIdSessionData

    /**
     * Launch context for this screen — distinguishes brand-new signup, account recovery,
     * and the legacy "existing user adding email" entry point. Read from a required nav arg.
     */
    private lateinit var workflow: EmailWorkFlow

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPersonalidEmailBinding.inflate(inflater, container, false)
        personalIdSessionData =
            ViewModelProvider(requireActivity())
                .get(PersonalIdSessionDataViewModel::class.java)
                .personalIdSessionData
        requireActivity().setTitle(R.string.personalid_email_appbar_title)
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        workflow = PersonalIdEmailFragmentArgs.fromBundle(requireArguments()).workflow

        setupListeners()
        enableContinueButton(false)
        binding.emailTextValue.addTextChangedListener(createEmailWatcher())
        setUpEnterKeyAction(binding.emailTextValue)
        setupKeyboardScrollListener(binding.personalidEmailScrollView)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        destroyKeyboardScrollListener(binding.personalidEmailScrollView)
    }

    override fun onResume() {
        super.onResume()
        binding.emailTextValue.requestFocus()
    }

    override fun keyboardEnterPressed() {
        if (StringUtils.isValidEmail(binding.emailTextValue.text?.toString())) {
            submitEmail()
        } else {
            KeyboardHelper.hideVirtualKeyboard(requireActivity())
        }
    }

    private fun createEmailWatcher() =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                enableContinueButton(StringUtils.isValidEmail(s?.toString()))
            }

            override fun afterTextChanged(s: Editable?) {}
        }

    private fun setupListeners() {
        binding.personalidEmailContinueButton.setOnClickListener { submitEmail() }
        binding.personalidEmailSkipButton.setOnClickListener { confirmSkipEmail() }
    }

    private fun confirmSkipEmail() {
        val commCareActivity = requireActivity() as CommCareActivity<*>
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_email_skip_confirm_title),
                getString(R.string.personalid_email_skip_confirm_message),
            )
        dialog.setPositiveButton(getString(R.string.personalid_link_app_yes)) { _, _ ->
            commCareActivity.dismissAlertDialog()
            skipEmail()
        }
        dialog.setNegativeButton(getString(R.string.personalid_link_app_no)) { _, _ ->
            commCareActivity.dismissAlertDialog()
        }
        commCareActivity.showAlertDialog(dialog)
    }

    private fun enableContinueButton(enabled: Boolean) {
        binding.personalidEmailContinueButton.isEnabled = enabled
    }

    private fun submitEmail() {
        val email =
            binding.emailTextValue.text
                .toString()
                .trim()
        if (!StringUtils.isValidEmail(email)) {
            showError(getString(R.string.personalid_email_invalid_format))
            return
        }
        FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(javaClass.simpleName, null)
        clearError()
        enableContinueButton(false)

        object : PersonalIdApiHandler<Boolean>() {
            override fun onSuccess(status: Boolean) {
                navigateToEmailVerification(email)
            }

            override fun onFailure(
                failureCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                showError(
                    PersonalIdOrConnectApiErrorHandler.handle(
                        requireActivity(),
                        failureCode,
                        t,
                    ),
                )
                enableContinueButton(true)
            }
        }.sendEmailOtp(
            requireActivity(),
            email,
            if (workflow == EmailWorkFlow.EXISTING_USER) null else personalIdSessionData.token,
            if (workflow == EmailWorkFlow.EXISTING_USER) ConnectUserDatabaseUtil.getUser(requireActivity()) else null,
        )
    }

    private fun skipEmail() {
        FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(javaClass.simpleName, "skip")
        when (workflow) {
            // Existing user adding email post-registration — finish the legacy entry activity.
            EmailWorkFlow.EXISTING_USER -> {
                requireActivity().finish()
            }

            // Recovery — finalize account recovery and show the success screen.
            EmailWorkFlow.RECOVERY -> {
                finalizeRecoveryAndShowSuccess()
            }

            // Brand-new signup — continue to photo capture.
            EmailWorkFlow.REGISTRATION -> {
                binding.root
                    .findNavController()
                    .navigate(PersonalIdEmailFragmentDirections.actionPersonalidEmailToPersonalidPhotoCapture())
            }
        }
    }

    private fun finalizeRecoveryAndShowSuccess() {
        PersonalIdRecoveryCompleter.finalizeAccountRecovery(requireActivity(), personalIdSessionData)
        navigateToMessageDisplay(
            getString(R.string.connect_recovery_success_title),
            getString(R.string.connect_recovery_success_message),
            isCancellable = false,
            phase = ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
            buttonText = R.string.ok,
        )
    }

    private fun navigateToEmailVerification(email: String) {
        val action =
            PersonalIdEmailFragmentDirections
                .actionPersonalidEmailToPersonalidEmailVerification(email, workflow)
        binding.root.findNavController().navigate(action)
    }

    private fun clearError() {
        binding.personalidEmailError.visibility = View.GONE
        binding.personalidEmailError.text = ""
    }

    private fun showError(message: String) {
        binding.personalidEmailError.visibility = View.VISIBLE
        binding.personalidEmailError.text = message
    }

    override fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    ) {
        val action =
            PersonalIdEmailFragmentDirections
                .actionPersonalidEmailToPersonalidMessage(
                    title,
                    message.orEmpty(),
                    phase,
                    getString(buttonText),
                    null,
                ).setIsCancellable(isCancellable)
        binding.root.findNavController().navigate(action)
    }

    companion object {
        const val ARG_ENTERED_EMAIL = "email"
        const val ARG_EMAIL_WORKFLOW = "workflow"
    }
}
