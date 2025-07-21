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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userName = it.getString(ARG_USERNAME)
            profilePic = it.getString(ARG_PROFILE_PIC)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEarnedCredentialBinding.inflate(inflater, container, false)

        earnedCredentialAdapter = EarnedCredentialAdapter(
            listener = object : EarnedCredentialAdapter.OnCredentialClickListener {
                override fun onViewCredentialClick(credential: PersonalIdCredential) {

                }
            },
            profilePic = profilePic ?: ""
        )

        binding!!.rvEarnedCredential.adapter = earnedCredentialAdapter

        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[PersonalIdCredentialViewModel::class.java]

        viewModel.earnedCredentials.observe(viewLifecycleOwner) { earnedList ->
            earnedCredentialAdapter.setData(earnedList)
        }
    }

    companion object {
        private const val ARG_USERNAME = "username"
        private const val ARG_PROFILE_PIC = "profile_pic"

        fun newInstance(username: String, profilePic: String): EarnedCredentialFragment {
            val fragment = EarnedCredentialFragment()
            val args = Bundle()
            args.putString(ARG_USERNAME, username)
            args.putString(ARG_PROFILE_PIC, profilePic)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
