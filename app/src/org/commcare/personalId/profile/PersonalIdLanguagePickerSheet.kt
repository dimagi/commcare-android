package org.commcare.personalId.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ItemPersonalidLanguageBinding
import org.commcare.dalvik.databinding.PersonalidLanguagePickerSheetBinding
import org.commcare.personalId.demographic.PersonalIdLanguage
import org.commcare.personalId.demographic.PersonalIdLanguages
import org.commcare.views.extensions.onTextChanged
import java.util.Locale

/**
 * Searchable multi-select list of languages. Its order is fixed when it opens (selected languages
 * first, then the device language) so rows don't jump while the worker ticks them. Done hands the
 * ticked codes back through [REQUEST_KEY]; dismissing without Done changes nothing.
 */
class PersonalIdLanguagePickerSheet : BottomSheetDialogFragment() {
    private var _binding: PersonalidLanguagePickerSheetBinding? = null
    val binding get() = _binding!!
    private lateinit var languages: List<PersonalIdLanguage>
    private lateinit var selectedCodes: MutableSet<String>
    private val adapter = LanguageAdapter()

    override fun getTheme(): Int = R.style.ConnectBottomSheetDialogTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialCodes = requireArguments().getStringArrayList(ARG_SELECTED_CODES).orEmpty()
        selectedCodes = (savedInstanceState?.getStringArrayList(STATE_SELECTED_CODES) ?: initialCodes).toMutableSet()
        languages =
            PersonalIdLanguages.orderForPicker(
                PersonalIdLanguages.all(requireContext()),
                initialCodes.toSet(),
                PersonalIdLanguages.deviceLanguageCode(),
            )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PersonalidLanguagePickerSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        expandToFullHeight(view)
        binding.languageList.layoutManager = LinearLayoutManager(requireContext())
        binding.languageList.adapter = adapter
        binding.languageSearch.onTextChanged { filter(it) }
        binding.languageDone.setOnClickListener { finishWithSelection() }
        filter(
            binding.languageSearch.text
                ?.toString()
                .orEmpty(),
        )
    }

    /**
     * The list takes whatever height is left under the search box, so the sheet must fill the screen and
     * resize for the keyboard; at its content height the list shrinks to nothing once the keyboard opens.
     */
    private fun expandToFullHeight(view: View) {
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        view.post {
            val sheet = view.parent as? View ?: return@post
            sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            (dialog as? BottomSheetDialog)?.behavior?.apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_SELECTED_CODES, ArrayList(selectedCodes))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun filter(query: String) {
        val needle = query.trim().lowercase(Locale.getDefault())
        adapter.submitList(languages.filter { needle.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(needle) })
    }

    private fun finishWithSelection() {
        val codes = languages.map { it.code }.filter { it in selectedCodes }
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CODES to ArrayList(codes)))
        dismiss()
    }

    private inner class LanguageAdapter : ListAdapter<PersonalIdLanguage, LanguageViewHolder>(LanguageDiff) {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ) = LanguageViewHolder(ItemPersonalidLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(
            holder: LanguageViewHolder,
            position: Int,
        ) {
            val language = getItem(position)
            holder.binding.languageCheckbox.apply {
                setOnCheckedChangeListener(null)
                text = language.label
                isChecked = language.code in selectedCodes
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedCodes.add(language.code) else selectedCodes.remove(language.code)
                }
            }
        }
    }

    private class LanguageViewHolder(
        val binding: ItemPersonalidLanguageBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    private object LanguageDiff : DiffUtil.ItemCallback<PersonalIdLanguage>() {
        override fun areItemsTheSame(
            oldItem: PersonalIdLanguage,
            newItem: PersonalIdLanguage,
        ) = oldItem.code == newItem.code

        override fun areContentsTheSame(
            oldItem: PersonalIdLanguage,
            newItem: PersonalIdLanguage,
        ) = oldItem == newItem
    }

    companion object {
        const val TAG = "PersonalIdLanguagePickerSheet"
        const val REQUEST_KEY = "personalid_language_picker"
        const val RESULT_CODES = "selected_codes"
        private const val ARG_SELECTED_CODES = "selected_codes"
        private const val STATE_SELECTED_CODES = "state_selected_codes"

        fun newInstance(selectedCodes: List<String>) =
            PersonalIdLanguagePickerSheet().apply {
                arguments = bundleOf(ARG_SELECTED_CODES to ArrayList(selectedCodes))
            }
    }
}
