package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.commcare.activities.connect.viewmodel.PersonalIdCredentialViewModel
import org.commcare.adapters.PendingCredentialAdapter
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.dalvik.databinding.FragmentPendingCredentialBinding

class PendingCredentialFragment : Fragment() {

    private var binding: FragmentPendingCredentialBinding? = null
    private lateinit var pendingCredentialAdapter: PendingCredentialAdapter
    private lateinit var viewModel: PersonalIdCredentialViewModel
    private var userName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userName = it.getString(ARG_USERNAME)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPendingCredentialBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pendingCredentialAdapter = PendingCredentialAdapter(listener = object :
            PendingCredentialAdapter.OnCredentialClickListener {
            override fun onViewCredentialClick(credential: PersonalIdWorkHistory) {

            }
        })
        binding!!.rvPendingCredential.adapter = pendingCredentialAdapter
        viewModel = ViewModelProvider(requireActivity())[PersonalIdCredentialViewModel::class.java]
        viewModel.pendingCredentials.observe(viewLifecycleOwner) { pendingList ->
            val hasItems = !pendingList.isNullOrEmpty()
            binding?.apply {
                tvNoPendingCredential.isVisible = !hasItems
                rvPendingCredential.isVisible = hasItems
            }
            pendingCredentialAdapter.setData(pendingList.orEmpty())
        }
    }

    companion object {
        private const val ARG_USERNAME = "username"

        fun newInstance(username: String): PendingCredentialFragment {
            val fragment = PendingCredentialFragment()
            val args = Bundle()
            args.putString(ARG_USERNAME, username)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
