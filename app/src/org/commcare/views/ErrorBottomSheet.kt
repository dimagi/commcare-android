package org.commcare.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.commcare.dalvik.databinding.ErrorBottomSheetBinding

class ErrorBottomSheet(
    private val title: String? = null,
    private val primaryButtonText: String? = null,
    private val onButtonClick: (() -> Unit)? = null
) : BottomSheetDialogFragment() {
    private var _binding: ErrorBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ErrorBottomSheetBinding.inflate(inflater, container, false)

        binding.tvTitle.text = title
        binding.btnOk.text = primaryButtonText

        binding.btnOk.setOnClickListener {
            dismiss()
            onButtonClick?.invoke()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

