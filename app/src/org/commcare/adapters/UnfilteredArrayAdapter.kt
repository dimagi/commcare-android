package org.commcare.adapters

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * Adapter for a read-only dropdown. A plain [ArrayAdapter] filters by the field's text, so once an
 * option is chosen (or restored after rotation) the dropdown would offer only that option.
 */
class UnfilteredArrayAdapter(
    context: Context,
    private val items: List<String>,
) : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {
    private val noFilter =
        object : Filter() {
            override fun performFiltering(constraint: CharSequence?) =
                FilterResults().apply {
                    values = items
                    count = items.size
                }

            override fun publishResults(
                constraint: CharSequence?,
                results: FilterResults?,
            ) = notifyDataSetChanged()
        }

    override fun getFilter(): Filter = noFilter
}
