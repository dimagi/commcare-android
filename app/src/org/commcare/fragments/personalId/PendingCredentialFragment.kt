package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.commcare.activities.connect.viewmodel.PersonalIdCredentialViewModel
import org.commcare.adapters.PendingCredentialAdapter
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.dalvik.databinding.FragmentPendingCredentialBinding

class PendingCredentialFragment : Fragment() {
    private var binding: FragmentPendingCredentialBinding? = null
    private lateinit var pendingCredentialAdapter: PendingCredentialAdapter
    private lateinit var viewModel: PersonalIdCredentialViewModel
    private var userName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPendingCredentialBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PersonalIdCredentialViewModel::class.java]
        viewModel.username.observe(viewLifecycleOwner) { name ->
            userName = name
        }
        viewModel.pendingCredentials.observe(viewLifecycleOwner) { pendingList ->
            setUpPendingRecyclerView(pendingList)
        }
    }

    private fun setUpPendingRecyclerView(pendingList: List<PersonalIdCredential>) {
        pendingCredentialAdapter = PendingCredentialAdapter(listener = object :
            PendingCredentialAdapter.OnCredentialClickListener {
            override fun onViewCredentialClick(credential: PersonalIdCredential) {

            }
        })
        binding!!.rvPendingCredential.adapter = pendingCredentialAdapter
        pendingCredentialAdapter.setData(pendingList)
    }
}