package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.commcare.activities.connect.viewmodel.PersonalIdWorkHistoryViewModel
import org.commcare.adapters.WorkHistoryEarnedAdapter
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.dalvik.databinding.FragmentWorkHistoryEarnedBinding

class WorkHistoryEarnedFragment : Fragment() {
    private var binding: FragmentWorkHistoryEarnedBinding? = null
    private lateinit var workHistoryEarnedAdapter: WorkHistoryEarnedAdapter
    private lateinit var viewModel: PersonalIdWorkHistoryViewModel
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
        binding = FragmentWorkHistoryEarnedBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        workHistoryEarnedAdapter = WorkHistoryEarnedAdapter(
            listener = object : WorkHistoryEarnedAdapter.OnWorkHistoryItemClickListener {
                override fun onWorkHistoryItemClick(workHistory: PersonalIdWorkHistory) {

                }
            },
            profilePic = profilePic ?: ""
        )
        binding!!.rvEarnedWorkHistory.adapter = workHistoryEarnedAdapter
        viewModel = ViewModelProvider(requireActivity())[PersonalIdWorkHistoryViewModel::class.java]
        viewModel.earnedWorkHistory.observe(viewLifecycleOwner) { earnedList ->
            if (earnedList.isNullOrEmpty()) {
                binding!!.tvNoWorkHistoryAvailable.visibility = View.VISIBLE
            } else {
                binding!!.tvNoWorkHistoryAvailable.visibility = View.GONE
            }
            workHistoryEarnedAdapter.setData(earnedList)
        }
    }

    companion object {
        private const val ARG_USERNAME = "username"
        private const val ARG_PROFILE_PIC = "profile_pic"

        fun newInstance(username: String, profilePic: String): WorkHistoryEarnedFragment {
            val fragment = WorkHistoryEarnedFragment()
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
