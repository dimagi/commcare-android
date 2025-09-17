package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.commcare.activities.connect.viewmodel.PersonalIdWorkHistoryViewModel
import org.commcare.adapters.WorkHistoryPendingAdapter
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.dalvik.databinding.FragmentWorkHistoryPendingBinding

class WorkHistoryPendingFragment : Fragment() {

    private var binding: FragmentWorkHistoryPendingBinding? = null
    private lateinit var workHistoryPendingAdapter: WorkHistoryPendingAdapter
    private lateinit var viewModel: PersonalIdWorkHistoryViewModel
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
        binding = FragmentWorkHistoryPendingBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        workHistoryPendingAdapter = WorkHistoryPendingAdapter(listener = object :
            WorkHistoryPendingAdapter.OnWorkHistoryItemClickListener {
            override fun onWorkHistoryItemClick(workHistory: PersonalIdWorkHistory) {

            }
        })
        binding!!.rvPendingWorkHistory.adapter = workHistoryPendingAdapter
        viewModel = ViewModelProvider(requireActivity())[PersonalIdWorkHistoryViewModel::class.java]
        viewModel.pendingWorkHistory.observe(viewLifecycleOwner) { pendingList ->
            val hasItems = !pendingList.isNullOrEmpty()
            binding?.apply {
                tvNoPendingWorkHistory.isVisible = !hasItems
                rvPendingWorkHistory.isVisible = hasItems
            }
            workHistoryPendingAdapter.setData(pendingList.orEmpty())
        }
    }

    companion object {
        private const val ARG_USERNAME = "username"

        fun newInstance(username: String): WorkHistoryPendingFragment {
            val fragment = WorkHistoryPendingFragment()
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
