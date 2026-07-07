package org.commcare.personalId.profile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.commcare.activities.CommCareActivity
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.PersonalidProfileEditScreenBinding
import org.commcare.fragments.personalId.EmailHelper
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.personalId.photo.PersonalIdPhotoUpdater

class PersonalIdProfileEditFragment : BasePersonalIdProfileFragment() {
    private var _binding: PersonalidProfileEditScreenBinding? = null
    val binding get() = _binding!!
    private lateinit var viewModel: PersonalIdProfileEditViewModel
    private lateinit var photoUpdater: PersonalIdPhotoUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[PersonalIdProfileEditViewModel::class.java]
        photoUpdater =
            PersonalIdPhotoUpdater(
                requireActivity() as CommCareActivity<*>,
                this,
                onSuccess = { photoBase64 ->
                    viewModel.onPhotoUpdated(photoBase64)
                    _binding?.let { loadUserPhoto(it.profileHeader.profileUserImage, photoBase64) }
                },
                onFailure = { _, _ ->
                    // No-op for the Profile screen. The app sidebar shows a warning icon in the other flow.
                },
            )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PersonalidProfileEditScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val displayModel = PersonalIdProfileDisplayModel.fromUserRecord(requireContext(), viewModel.user)
        renderProfileHeader(binding.profileHeader, displayModel)
        binding.profilePhoneEditText.setText(displayModel.displayPhone)
        binding.profileNameEditText.setText(viewModel.currentName)
        binding.profileEmailEditText.setText(viewModel.currentEmail)

        binding.profileHeader.profileUserImageCard.setOnClickListener {
            photoUpdater.initiatePhotoUpdate()
        }
        afterTextChanged(binding.profileNameEditText) {
            viewModel.onNameChanged(it)
            refreshFormState()
        }
        afterTextChanged(binding.profileEmailEditText) {
            viewModel.onEmailChanged(it)
            refreshFormState()
        }
        binding.btnCancel.setOnClickListener { handleBack() }
        binding.btnSave.setOnClickListener { onSaveClicked() }
        refreshFormState()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBack()
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        refreshFormState()
    }

    private fun handleBack() {
        if (!viewModel.isModified()) {
            findNavController().popBackStack()
            return
        }
        showConfirmationDialog(
            title = getString(R.string.personalid_profile_edit_discard_title),
            message = getString(R.string.personalid_profile_edit_discard_message),
            positiveText = getString(R.string.personalid_profile_edit_discard_positive),
            negativeText = getString(R.string.personalid_profile_edit_discard_negative),
        ) {
            findNavController().popBackStack()
        }
    }

    private fun afterTextChanged(
        editText: EditText,
        onChanged: (String) -> Unit,
    ) {
        editText.addTextChangedListener(
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
                ) {}

                override fun afterTextChanged(s: Editable?) {
                    onChanged(s?.toString().orEmpty())
                }
            },
        )
    }

    private fun refreshFormState() {
        binding.btnSave.isEnabled = viewModel.canSave()
        binding.profileInputEmail.error = emailErrorMessage()
    }

    private fun emailErrorMessage(): String? =
        when {
            viewModel.isEmailValid() -> null
            viewModel.isEmailEmpty() -> getString(R.string.personalid_profile_edit_error_email_required)
            else -> getString(R.string.personalid_profile_edit_error_email_invalid)
        }

    private fun onSaveClicked() {
        if (viewModel.isEmailModified()) {
            showEmailOtpConfirmationDialog()
        } else {
            saveProfileDetails {
                Toast
                    .makeText(
                        requireActivity(),
                        getString(R.string.personalid_profile_edit_save_success),
                        Toast.LENGTH_LONG,
                    ).show()
                findNavController().popBackStack()
            }
        }
    }

    private fun saveProfileDetails(onSaved: () -> Unit) {
        binding.btnSave.isEnabled = false
        val user = viewModel.user
        object : PersonalIdApiHandler<Boolean>() {
            override fun onSuccess(success: Boolean) {
                viewModel.commitProfileDetails()
                _binding ?: return
                onSaved()
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                _binding ?: return
                onSaveFailed(errorCode, t)
            }
        }.updateProfile(requireActivity(), user.userId, user.password, viewModel.currentName, null, null)
    }

    private fun showEmailOtpConfirmationDialog() {
        val newEmail = viewModel.currentEmail
        showConfirmationDialog(
            title = getString(R.string.personalid_profile_edit_otp_confirm_title),
            message = getString(R.string.personalid_profile_edit_otp_confirm_message, newEmail),
            positiveText = getString(R.string.personalid_profile_edit_otp_confirm_positive),
            negativeText = getString(R.string.personalid_profile_edit_otp_confirm_negative),
        ) {
            binding.btnSave.isEnabled = false
            if (viewModel.isNameModified()) {
                saveProfileDetails { sendEmailOtpAndNavigate(newEmail) }
            } else {
                sendEmailOtpAndNavigate(newEmail)
            }
        }
    }

    private fun sendEmailOtpAndNavigate(newEmail: String) {
        EmailHelper.sendEmailOtp(
            activity = requireActivity(),
            email = newEmail,
            workflow = EmailWorkFlow.EXISTING_USER,
            sessionData = null,
            tracker = viewModel.emailOtpTracker,
            onSuccess = {
                _binding ?: return@sendEmailOtp
                findNavController().navigate(
                    PersonalIdProfileEditFragmentDirections.actionProfileEditToEmailVerification(
                        newEmail,
                        EmailWorkFlow.EXISTING_USER,
                        viewModel.emailOtpTracker.requestCount,
                    ),
                )
            },
            onFailure = { errorCode, t ->
                _binding ?: return@sendEmailOtp
                onSaveFailed(errorCode, t)
            },
        )
    }

    private fun onSaveFailed(
        errorCode: PersonalIdOrConnectApiErrorCodes,
        t: Throwable?,
    ) {
        binding.btnSave.isEnabled = viewModel.canSave()
        Toast
            .makeText(
                requireActivity(),
                PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), errorCode, t),
                Toast.LENGTH_LONG,
            ).show()
    }
}
