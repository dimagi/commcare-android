package org.commcare.personalId.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.commcare.activities.CommCareActivity
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.personalId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.PersonalidProfileEditScreenBinding
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.photo.PersonalIdPhotoUpdater
import org.commcare.views.extensions.onTextChanged

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
                    FirebaseAnalyticsUtil.reportPersonalIdProfileAction(
                        AnalyticsParamValue.MANAGE_PROFILE_ACTION_PHOTO_UPDATED,
                        AnalyticsParamValue.MANAGE_PROFILE_OUTCOME_SUCCESS,
                    )
                },
                onFailure = { _, _ ->
                    // No UI feedback on the Profile screen; the app sidebar shows a warning icon in the other flow.
                    FirebaseAnalyticsUtil.reportPersonalIdProfileAction(
                        AnalyticsParamValue.MANAGE_PROFILE_ACTION_PHOTO_UPDATED,
                        AnalyticsParamValue.MANAGE_PROFILE_OUTCOME_FAILURE,
                    )
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
        binding.profileNameEditText.onTextChanged {
            viewModel.onNameChanged(it)
            refreshFormState()
        }
        binding.profileEmailEditText.onTextChanged {
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
            FirebaseAnalyticsUtil.reportPersonalIdProfileAction(
                AnalyticsParamValue.MANAGE_PROFILE_ACTION_CHANGES_DISCARDED,
                null,
            )
            findNavController().popBackStack()
        }
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
            navigateToBackupCodeForEmailChange()
        } else {
            saveProfileDetails {
                showSuccess()
                findNavController().popBackStack()
            }
        }
    }

    private fun navigateToBackupCodeForEmailChange() {
        val pendingEmail = viewModel.currentEmail
        if (viewModel.isNameModified()) {
            binding.btnSave.isEnabled = false
            saveProfileDetails { navigateToBackupCode(pendingEmail) }
        } else {
            navigateToBackupCode(pendingEmail)
        }
    }

    private fun navigateToBackupCode(pendingEmail: String) {
        _binding ?: return
        findNavController().navigate(
            PersonalIdProfileEditFragmentDirections
                .actionProfileEditToBackupCode(EmailWorkFlow.EXISTING_USER)
                .setPendingEmail(pendingEmail)
        )
    }

    private fun saveProfileDetails(onSaved: () -> Unit) {
        binding.btnSave.isEnabled = false
        val user = viewModel.user
        object : PersonalIdApiHandler<Boolean>() {
            override fun onSuccess(success: Boolean) {
                reportProfileSaveOutcome(AnalyticsParamValue.MANAGE_PROFILE_OUTCOME_SUCCESS)
                viewModel.commitProfileDetails()
                _binding ?: return
                onSaved()
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                reportProfileSaveOutcome(AnalyticsParamValue.MANAGE_PROFILE_OUTCOME_FAILURE)
                _binding ?: return
                onSaveFailed(errorCode, t)
            }
        }.updateProfile(requireActivity(), user.userId, user.password, viewModel.currentName, null, null)
    }

    private fun reportProfileSaveOutcome(outcome: String) {
        if (viewModel.isNameModified()) {
            FirebaseAnalyticsUtil.reportPersonalIdProfileAction(
                AnalyticsParamValue.MANAGE_PROFILE_ACTION_NAME_UPDATED,
                outcome,
            )
        }
    }

    private fun onSaveFailed(
        errorCode: PersonalIdOrConnectApiErrorCodes,
        throwable: Throwable?,
    ) {
        binding.btnSave.isEnabled = viewModel.canSave()
        showError(errorCode, throwable)
    }

    private fun showSuccess() {
        val message = getString(R.string.personalid_profile_edit_save_success)
        Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show()
    }

    private fun showError(
        errorCode: PersonalIdOrConnectApiErrorCodes,
        throwable: Throwable?,
    ) {
        val message = PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), errorCode, throwable)
        Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show()
    }
}
