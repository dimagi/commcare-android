package org.commcare.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.StringRes
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
import org.commcare.dalvik.databinding.BottomSheetListPickerBinding
import org.commcare.dalvik.databinding.ItemListPickerBinding
import org.commcare.views.extensions.onTextChanged
import java.util.Locale

/**
 * Searchable multi-select list in a bottom sheet. Items keep the order the caller gives so rows don't
 * jump while they are ticked. Done hands the ticked ids back through the caller's request key, under
 * [RESULT_SELECTED_IDS]; dismissing without Done changes nothing.
 */
class BottomSheetListPicker : BottomSheetDialogFragment() {
    private var _binding: BottomSheetListPickerBinding? = null
    val binding get() = _binding!!
    private lateinit var items: List<Item>
    private lateinit var selectedIds: MutableSet<String>
    private val adapter = ItemAdapter()

    data class Item(
        val id: String,
        val label: String,
    )

    override fun getTheme(): Int = R.style.ConnectBottomSheetDialogTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        val ids = args.getStringArrayList(ARG_IDS).orEmpty()
        val labels = args.getStringArrayList(ARG_LABELS).orEmpty()
        items = ids.zip(labels) { id, label -> Item(id, label) }
        val initialIds = args.getStringArrayList(ARG_SELECTED_IDS).orEmpty()
        selectedIds = (savedInstanceState?.getStringArrayList(STATE_SELECTED_IDS) ?: initialIds).toMutableSet()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetListPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        binding.pickerTitle.setText(args.getInt(ARG_TITLE))
        binding.pickerSearch.setHint(args.getInt(ARG_SEARCH_HINT))
        binding.pickerDone.setText(args.getInt(ARG_DONE_LABEL))
        expandToFullHeight(view)
        binding.pickerList.layoutManager = LinearLayoutManager(requireContext())
        binding.pickerList.adapter = adapter
        binding.pickerSearch.onTextChanged { filter(it) }
        binding.pickerDone.setOnClickListener { finishWithSelection() }
        filter(
            binding.pickerSearch.text
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
        outState.putStringArrayList(STATE_SELECTED_IDS, ArrayList(selectedIds))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun filter(query: String) {
        val needle = query.trim().lowercase(Locale.getDefault())
        adapter.submitList(items.filter { needle.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(needle) })
    }

    private fun finishWithSelection() {
        val ids = items.map { it.id }.filter { it in selectedIds }
        setFragmentResult(requireArguments().getString(ARG_REQUEST_KEY)!!, bundleOf(RESULT_SELECTED_IDS to ArrayList(ids)))
        dismiss()
    }

    private inner class ItemAdapter : ListAdapter<Item, ItemViewHolder>(ItemDiff) {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ) = ItemViewHolder(ItemListPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(
            holder: ItemViewHolder,
            position: Int,
        ) {
            val item = getItem(position)
            holder.binding.pickerCheckbox.apply {
                setOnCheckedChangeListener(null)
                text = item.label
                isChecked = item.id in selectedIds
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedIds.add(item.id) else selectedIds.remove(item.id)
                }
            }
        }
    }

    private class ItemViewHolder(
        val binding: ItemListPickerBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    private object ItemDiff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(
            oldItem: Item,
            newItem: Item,
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: Item,
            newItem: Item,
        ) = oldItem == newItem
    }

    companion object {
        const val RESULT_SELECTED_IDS = "selected_ids"
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_IDS = "ids"
        private const val ARG_LABELS = "labels"
        private const val ARG_SELECTED_IDS = "selected_ids"
        private const val ARG_TITLE = "title"
        private const val ARG_SEARCH_HINT = "search_hint"
        private const val ARG_DONE_LABEL = "done_label"
        private const val STATE_SELECTED_IDS = "state_selected_ids"

        fun newInstance(
            requestKey: String,
            items: List<Item>,
            selectedIds: List<String>,
            @StringRes title: Int,
            @StringRes searchHint: Int,
            @StringRes doneLabel: Int,
        ) = BottomSheetListPicker().apply {
            arguments =
                bundleOf(
                    ARG_REQUEST_KEY to requestKey,
                    ARG_IDS to ArrayList(items.map { it.id }),
                    ARG_LABELS to ArrayList(items.map { it.label }),
                    ARG_SELECTED_IDS to ArrayList(selectedIds),
                    ARG_TITLE to title,
                    ARG_SEARCH_HINT to searchHint,
                    ARG_DONE_LABEL to doneLabel,
                )
        }
    }
}
