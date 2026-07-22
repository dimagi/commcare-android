package org.commcare.personalId.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.PersonalidProfileScreenBinding

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
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile()
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
    }
}
