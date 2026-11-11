package org.commcare.fragments.connect

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
        configureBottomSheet(view)

        val job = (requireActivity() as ConnectActivity).activeJob
        val modules = job.learnAppInfo.learnModules
        binding.tvModulesTitle.text =
            resources.getQuantityString(
                R.plurals.connect_opportunity_learn_modules_label,
                modules.size,
            )
        binding.rvModules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvModules.adapter = ConnectLearnModuleAdapter(modules)
    }

    private fun configureBottomSheet(view: View) {
        view.post {
            val bottomSheet = view.parent as? View ?: return@post
            bottomSheet.layoutParams =
                bottomSheet.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            bottomSheet.background =
                ColorDrawable(ContextCompat.getColor(view.context, R.color.transparent))
            BottomSheetBehavior.from(bottomSheet).apply {
                peekHeight = resources.displayMetrics.heightPixels / 2
                state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
