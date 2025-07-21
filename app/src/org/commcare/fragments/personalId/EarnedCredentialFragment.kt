package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.commcare.activities.connect.viewmodel.PersonalIdCredentialViewModel
import org.commcare.adapters.EarnedCredentialAdapter
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.dalvik.databinding.FragmentEarnedCredentialBinding

class EarnedCredentialFragment : Fragment() {
    private var binding: FragmentEarnedCredentialBinding? = null
    private lateinit var earnedCredentialAdapter: EarnedCredentialAdapter
    private lateinit var viewModel: PersonalIdCredentialViewModel
    private var userName: String? = null
    private var profilePic: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PersonalIdCredentialViewModel::class.java]
        viewModel.username.observe(viewLifecycleOwner) { name ->
            userName = name
        }

        viewModel.profilePic.observe(viewLifecycleOwner) { pic ->
            profilePic = pic
        }
        viewModel.earnedCredentials.observe(viewLifecycleOwner) { earnedList ->
            setUpEarnedRecyclerView(earnedList)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEarnedCredentialBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    private fun setUpEarnedRecyclerView(earnedList: List<PersonalIdCredential>) {
        earnedCredentialAdapter = EarnedCredentialAdapter(listener = object :
            EarnedCredentialAdapter.OnCredentialClickListener {
            override fun onViewCredentialClick(credential: PersonalIdCredential) {

            }
        },profilePic!!)
        binding!!.rvEarnedCredential.adapter = earnedCredentialAdapter
        earnedCredentialAdapter.setData(earnedList)
    }
}