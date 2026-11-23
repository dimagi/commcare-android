package org.commcare.personalId.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.PersonalidProfileScreenBinding
import org.commcare.fragments.personalId.BackupCodeWorkflow
import org.commcare.fragments.personalId.EmailWorkFlow

class PersonalIdProfileFragment : BasePersonalIdProfileFragment() {
    private var _binding: PersonalidProfileScreenBinding? = null
    val binding get() = _binding!!
    private lateinit var viewModel: PersonalIdProfileViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PersonalidProfileScreenBinding.inflate(inflater, container, false)
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
        setupMenu()
        viewModel = ViewModelProvider(this)[PersonalIdProfileViewModel::class.java]
        viewModel.profileDisplayModel.observe(viewLifecycleOwner) { displayProfileDetails(it) }
        binding.profileBtnForgetPersonalid.setOnClickListener { showForgetPersonalIdDialog() }
        binding.profileChangeBackupCode.setOnClickListener { onChangeBackupCodeClicked() }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile()
    }

    private fun onChangeBackupCodeClicked() {
        val user = ConnectUserDatabaseUtil.getUser()!!
        if (user.pin.isNullOrEmpty()) {
            val email = user.email
            if (email != null) {
                navigateToSendEmailOtp(email, EmailWorkFlow.PENDING_BACKUP_CODE)
            } else {
                (requireActivity() as PersonalIdProfileActivity).showAddEmailToast()
            }
        } else {
            navigateToProfileBackupCode()
        }
    }

    private fun navigateToProfileBackupCode() {
        findNavController().navigate(
            PersonalIdProfileFragmentDirections
                .actionProfileToProfileBackupCode(
                    EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER,
                    BackupCodeWorkflow.CHANGE_BACKUP_CODE,
                ),
        )
    }

    private fun navigateToSendEmailOtp(
        email: String,
        workFlow: EmailWorkFlow,
    ) {
        val directions =
            PersonalIdProfileFragmentDirections
                .actionProfileToSendEmailOtp(email, workFlow)
                .setMasked(true)
        findNavController().navigate(directions)
    }

    private fun showForgetPersonalIdDialog() {
        showConfirmationDialog(
            title = getString(R.string.personalid_profile_forget_confirm_title),
            message = getString(R.string.personalid_profile_forget_confirm_message),
            positiveText = getString(R.string.ok),
            negativeText = getString(R.string.cancel),
        ) {
            (requireActivity() as PersonalIdProfileActivity).forgetPersonalIdAccount()
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    menuInflater.inflate(R.menu.personalid_profile_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                    when (menuItem.itemId) {
                        R.id.action_profile_edit -> {
                            findNavController().navigate(R.id.action_profile_to_profile_edit)
                            true
                        }

                        else -> {
                            false
                        }
                    }
            },
            viewLifecycleOwner,
        )
    }

    private fun displayProfileDetails(profileDisplayModel: PersonalIdProfileDisplayModel) {
        renderProfileHeader(binding.profileHeader, profileDisplayModel)
        binding.profileValueName.text = profileDisplayModel.name
        binding.profileValuePhone.text = profileDisplayModel.displayPhone
        binding.profileValueEmail.text = profileDisplayModel.email

        val notSet = getString(R.string.personalid_profile_value_not_set)
        binding.profileValueGender.text = profileDisplayModel.gender ?: notSet
        binding.profileValueDateOfBirth.text = profileDisplayModel.dateOfBirth ?: notSet
        binding.profileValueEducation.text = profileDisplayModel.education ?: notSet
        binding.profileValueChwExperience.text = profileDisplayModel.chwExperience ?: notSet
        binding.profileValueTechProficiency.text = profileDisplayModel.techProficiency ?: notSet
        LanguageChips.render(binding.profileValueLanguages, profileDisplayModel.languages, onRemove = null)
        binding.profileValueLanguagesNotSet.isVisible = profileDisplayModel.languages.isEmpty()
    }
}
