package org.commcare.fragments.connect

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.commcare.activities.connect.ConnectActivity
import org.commcare.adapters.ConnectLearnModuleAdapter
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectLearnModulesSheetBinding

class ConnectLearnModulesBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentConnectLearnModulesSheetBinding? = null
    val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ConnectBottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentConnectLearnModulesSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        expandToFullHeight(view)

        val job = (requireActivity() as ConnectActivity).activeJob
        binding.tvModulesTitle.text =
            getString(R.string.connect_opportunity_modules_sheet_title, job.title)
        binding.rvModules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvModules.adapter =
            ConnectLearnModuleAdapter(job.learnAppInfo.learnModules)
    }

    private fun expandToFullHeight(view: View) {
        view.post {
            val dialog = dialog as? BottomSheetDialog ?: return@post
            val bottomSheet =
                dialog.findViewById<FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet,
                ) ?: return@post
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheet.layoutParams =
                bottomSheet.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            bottomSheet.background =
                ColorDrawable(ContextCompat.getColor(requireContext(), R.color.transparent))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
