package org.commcare.personalId.profile

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
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.PersonalidProfileScreenBinding

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
