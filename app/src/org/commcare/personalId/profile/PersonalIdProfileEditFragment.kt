package org.commcare.personalId.profile

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.commcare.activities.CommCareActivity
import org.commcare.adapters.UnfilteredArrayAdapter
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.personalId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.PersonalidProfileEditScreenBinding
import org.commcare.fragments.personalId.BackupCodeWorkflow
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.photo.PersonalIdPhotoUpdater
import org.commcare.personalId.profile.demographic.ChwExperience
import org.commcare.personalId.profile.demographic.DateOfBirth
import org.commcare.personalId.profile.demographic.DemographicOption
import org.commcare.personalId.profile.demographic.Education
import org.commcare.personalId.profile.demographic.Gender
import org.commcare.personalId.profile.demographic.PersonalIdLanguages
import org.commcare.personalId.profile.demographic.TechProficiency
import org.commcare.views.BottomSheetListPicker
import org.commcare.views.extensions.onTextChanged

class PersonalIdProfileEditFragment : BasePersonalIdProfileFragment() {
    private var _binding: PersonalidProfileEditScreenBinding? = null
    val binding get() = _binding!!
    private lateinit var viewModel: PersonalIdProfileEditViewModel
    private lateinit var photoUpdater: PersonalIdPhotoUpdater
    private var defaultStrokeColor: ColorStateList? = null

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

        binding.profileHeader.profileUserImageCameraOverlay.visibility = View.VISIBLE
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
        bindDemographicFields()
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
        refreshDemographicWarnings()
    }

    private fun bindDemographicFields() {
        defaultStrokeColor =
            ContextCompat.getColorStateList(requireContext(), com.google.android.material.R.color.mtrl_outlined_stroke_color)
        bindDropdown(binding.profileGenderDropdown, Gender.entries, viewModel.currentGender, viewModel::onGenderChanged)
        bindDropdown(binding.profileEducationDropdown, Education.entries, viewModel.currentEducation, viewModel::onEducationChanged)
        bindDropdown(
            binding.profileChwExperienceDropdown,
            ChwExperience.entries,
            viewModel.currentChwExperience,
            viewModel::onChwExperienceChanged,
        )
        bindDropdown(
            binding.profileTechProficiencyDropdown,
            TechProficiency.entries,
            viewModel.currentTechProficiency,
            viewModel::onTechProficiencyChanged,
        )

        binding.profileDateOfBirthEditText.setText(DateOfBirth.formatDate(viewModel.currentDateOfBirth))
        binding.profileDateOfBirthEditText.setOnClickListener { showDateOfBirthPicker() }

        renderLanguages()
        binding.profileInputLanguages.setOnClickListener { showLanguagePicker() }
        childFragmentManager.setFragmentResultListener(LANGUAGE_PICKER_REQUEST_KEY, viewLifecycleOwner) { _, result ->
            val codes = result.getStringArrayList(BottomSheetListPicker.RESULT_SELECTED_IDS).orEmpty()
            viewModel.onLanguagesChanged(PersonalIdLanguages.joinCodes(codes))
            renderLanguages()
            refreshFormState()
        }
    }

    private fun <T : DemographicOption> bindDropdown(
        dropdown: MaterialAutoCompleteTextView,
        options: List<T>,
        currentKey: String?,
        onChanged: (String?) -> Unit,
    ) {
        val labels = options.map { getString(it.labelRes) }
        dropdown.setAdapter(UnfilteredArrayAdapter(requireContext(), labels))
        options.firstOrNull { it.key == currentKey }?.let { dropdown.setText(getString(it.labelRes), false) }
        dropdown.setOnItemClickListener { _, _, position, _ ->
            onChanged(options[position].key)
            refreshFormState()
        }
    }

    private fun showDateOfBirthPicker() {
        val today = MaterialDatePicker.todayInUtcMilliseconds()
        val picker =
            MaterialDatePicker.Builder
                .datePicker()
                .setTitleText(R.string.personalid_profile_field_date_of_birth)
                .setSelection(DateOfBirth.toUtcMillis(viewModel.currentDateOfBirth) ?: today)
                .setCalendarConstraints(
                    CalendarConstraints
                        .Builder()
                        .setEnd(today)
                        .setValidator(DateValidatorPointBackward.before(today))
                        .build(),
                ).build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            val date = DateOfBirth.fromUtcMillis(utcMillis)
            viewModel.onDateOfBirthChanged(date)
            _binding?.profileDateOfBirthEditText?.setText(DateOfBirth.formatDate(date))
            refreshFormState()
        }
        picker.show(childFragmentManager, DATE_OF_BIRTH_PICKER_TAG)
    }

    private fun showLanguagePicker() {
        if (childFragmentManager.findFragmentByTag(LANGUAGE_PICKER_TAG) != null) return
        val selectedCodes = PersonalIdLanguages.parseCodes(viewModel.currentLanguages)
        val languages =
            PersonalIdLanguages.orderForPicker(
                PersonalIdLanguages.all(requireContext()),
                selectedCodes.toSet(),
                PersonalIdLanguages.deviceLanguageCode(),
            )
        BottomSheetListPicker
            .newInstance(
                requestKey = LANGUAGE_PICKER_REQUEST_KEY,
                items = languages.map { BottomSheetListPicker.Item(it.code, it.label) },
                selectedIds = selectedCodes,
                title = R.string.personalid_profile_languages_select,
                searchHint = R.string.personalid_profile_languages_search,
                doneLabel = R.string.personalid_profile_languages_done,
            ).show(childFragmentManager, LANGUAGE_PICKER_TAG)
    }

    /** Codes no longer in the language list are kept in storage but get no chip, so a save doesn't drop them. */
    private fun renderLanguages() {
        val codes = PersonalIdLanguages.parseCodes(viewModel.currentLanguages)
        val labelsByCode = PersonalIdLanguages.all(requireContext()).associate { it.code to it.label }
        val shownCodes = codes.filter { it in labelsByCode }
        binding.profileLanguagesHint.isVisible = shownCodes.isEmpty()
        LanguageChips.render(binding.profileLanguagesChips, shownCodes.map { labelsByCode.getValue(it) }) { index ->
            viewModel.onLanguagesChanged(PersonalIdLanguages.joinCodes(codes - shownCodes[index]))
            renderLanguages()
            refreshFormState()
        }
    }

    /** An empty demographic field gets a warning outline as a reminder; it is not a validation error. */
    private fun refreshDemographicWarnings() {
        val warning = MaterialColors.getColor(binding.root, R.attr.connectStatusWarning)
        // A single-colour list only replaces the focused outline; listing the states colours the resting one too.
        val warningStroke =
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(warning, warning),
            )
        listOf(
            binding.profileInputGender to viewModel.currentGender,
            binding.profileInputDateOfBirth to viewModel.currentDateOfBirth,
            binding.profileInputEducation to viewModel.currentEducation,
            binding.profileInputChwExperience to viewModel.currentChwExperience,
            binding.profileInputTechProficiency to viewModel.currentTechProficiency,
        ).forEach { (field, value) ->
            val stroke = if (value.isNullOrEmpty()) warningStroke else defaultStrokeColor
            stroke?.let { field.setBoxStrokeColorStateList(it) }
        }
        val languagesEmpty = PersonalIdLanguages.parseCodes(viewModel.currentLanguages).isEmpty()
        binding.profileInputLanguages.strokeColor =
            if (languagesEmpty) warning else MaterialColors.getColor(binding.root, R.attr.connectOutline)
    }

    private fun emailErrorMessage(): String? =
        when {
            viewModel.isEmailValid() -> null
            viewModel.isEmailEmpty() -> getString(R.string.personalid_profile_edit_error_email_required)
            else -> getString(R.string.personalid_profile_edit_error_email_invalid)
        }

    private fun onSaveClicked() {
        if (viewModel.isDemographicsModified()) {
            viewModel.commitDemographics()
        }
        when {
            viewModel.isEmailModified() -> {
                startEmailChange()
            }

            viewModel.isNameModified() -> {
                saveProfileDetails {
                    showSuccess()
                    findNavController().popBackStack()
                }
            }

            else -> {
                showSuccess()
                findNavController().popBackStack()
            }
        }
    }

    private fun startEmailChange() {
        val pendingEmail = viewModel.currentEmail
        if (viewModel.isNameModified()) {
            binding.btnSave.isEnabled = false
            saveProfileDetails { startEmailChangeAuth(pendingEmail) }
        } else {
            startEmailChangeAuth(pendingEmail)
        }
    }

    private fun startEmailChangeAuth(pendingEmail: String) {
        _binding ?: return
        val directions =
            if (viewModel.user.pin.isNullOrEmpty()) {
                FirebaseAnalyticsUtil.reportPersonalIdAccountSecurityAction(
                    AnalyticsParamValue.ACCOUNT_SECURITY_WORKFLOW_EMAIL_CHANGE_PHONE_OTP,
                    AnalyticsParamValue.ACCOUNT_SECURITY_EVENT_PHONE_OTP_REQUIRED,
                    null,
                    null,
                    null,
                )
                PersonalIdProfileEditFragmentDirections
                    .actionProfileEditToProfileSendPhoneOtp(pendingEmail)
            } else {
                PersonalIdProfileEditFragmentDirections
                    .actionProfileEditToBackupCode(
                        EmailWorkFlow.EXISTING_USER,
                        BackupCodeWorkflow.EMAIL_CHANGE,
                    ).setPendingEmail(pendingEmail)
            }
        findNavController().navigate(directions)
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

    companion object {
        private const val DATE_OF_BIRTH_PICKER_TAG = "date_of_birth_picker"
        const val LANGUAGE_PICKER_TAG = "language_picker"
        private const val LANGUAGE_PICKER_REQUEST_KEY = "language_picker"
    }
}
