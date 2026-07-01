package org.commcare.personalId.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.commcare.activities.DispatchActivity
import org.commcare.connect.PersonalIdManager
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.PersonalidProfileScreenBinding
import org.commcare.views.dialogs.StandardAlertDialog

class PersonalIdProfileFragment : Fragment() {
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
        binding.profileBtnForgetPersonalid.setOnClickListener { showForgetConfirmationDialog() }
    }

    private fun showForgetConfirmationDialog() {
        val dialog =
            StandardAlertDialog(
                getString(R.string.personalid_profile_forget_confirm_title),
                getString(R.string.personalid_profile_forget_confirm_message),
            )
        dialog.setPositiveButton(getString(R.string.ok)) { dialogInterface, _ ->
            dialogInterface.dismiss()
            forgetPersonalId()
        }
        dialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        dialog.makeCancelable()
        dialog.showNonPersistentDialog(requireActivity())
    }

    private fun forgetPersonalId() {
        PersonalIdManager.getInstance().forgetUser()
        val intent =
            Intent(requireActivity(), DispatchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        startActivity(intent)
        requireActivity().finish()
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
        binding.profileName.text = profileDisplayModel.name
        binding.profilePhoneSubtitle.text = profileDisplayModel.displayPhone
        binding.profileValueName.text = profileDisplayModel.name
        binding.profileValuePhone.text = profileDisplayModel.displayPhone
        binding.profileValueEmail.text = profileDisplayModel.email
        Glide
            .with(binding.profileUserImage)
            .load(profileDisplayModel.photoBase64)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.nav_drawer_person_avatar)
                    .error(R.drawable.nav_drawer_person_avatar),
            ).into(binding.profileUserImage)
    }
}
