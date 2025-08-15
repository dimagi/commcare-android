package org.commcare.navdrawer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.commcare.dalvik.R

/**
 * Adapter to manage a flattened list of parent and child items in a single RecyclerView
 * for the navigation drawer.
 *
 * Supports dynamic expansion/collapse of parent items to reveal child items.
 *
 * @param context The context for inflating layouts
 * @param recyclerList List of parent items with their optional child items
 * @param onParentClick Callback invoked when a parent item is clicked
 * @param onChildClick Callback invoked when a child item is clicked
 */
class NavDrawerAdapter(
    private val context: Context,
    private var recyclerList: List<NavDrawerItem.ParentItem>,
    private val onParentClick: (NavDrawerItem.ParentItem) -> Unit,
    private val onChildClick: (BaseDrawerController.NavItemType, NavDrawerItem.ChildItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var displayList: List<NavDrawerItem> = flattenDrawerItems(recyclerList)

    companion object {
        private const val TYPE_PARENT = 0
        private const val TYPE_CHILD = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayList[position]) {
            is NavDrawerItem.ParentItem -> TYPE_PARENT
            is NavDrawerItem.ChildItem -> TYPE_CHILD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return if (viewType == TYPE_PARENT) {
            val view = inflater.inflate(R.layout.nav_drawer_list_item, parent, false)
            ParentViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.nav_drawer_sublist_item, parent, false)
            ChildViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayList[position]) {
            is NavDrawerItem.ParentItem -> (holder as ParentViewHolder).bind(item)
            is NavDrawerItem.ChildItem -> (holder as ChildViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = displayList.size

    /**
     * ViewHolder for parent items (top-level drawer entries).
     */
    inner class ParentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.list_title)
        private val icon = itemView.findViewById<ImageView>(R.id.list_icon)
        private val arrow = itemView.findViewById<ImageView>(R.id.arrow_icon)

        /**
         * Binds a parent item and sets up click listener to toggle expansion.
         */
        fun bind(item: NavDrawerItem.ParentItem) {
            title.text = item.title
            icon.setImageResource(item.iconResId)
            if (item.children.isNotEmpty()) {
                arrow.visibility = View.VISIBLE
                arrow.setImageResource(
                    if (item.isExpanded) R.drawable.nav_drawer_arrow_down
                    else R.drawable.ic_blue_forward
                )
            } else {
                arrow.visibility = View.GONE
            }

            if (item.isEnabled) {
                title.isEnabled = true
                icon.setColorFilter(ContextCompat.getColor(context, R.color.white))
            } else {
                title.isEnabled = false
                icon.setColorFilter(ContextCompat.getColor(context, R.color.nav_drawer_disable_color))
            }

            itemView.setOnClickListener {
                item.isExpanded = !item.isExpanded
                onParentClick(item)
                refreshList(recyclerList)
            }
        }
    }

    /**
     * ViewHolder for child items (sub-items under parent).
     */
    inner class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val childText = itemView.findViewById<TextView>(R.id.sublist_title)
        private val highlight = itemView.findViewById<ImageView>(R.id.sublist_highlight_icon)

        /**
         * Binds a child item and sets up click listener.
         */
        fun bind(item: NavDrawerItem.ChildItem) {
            childText.text = item.childTitle
            highlight.visibility = if (item.isHighlighted) View.VISIBLE else View.INVISIBLE
            itemView.setOnClickListener { onChildClick(item.parentType, item) }
        }
    }

    /**
     * Refreshes the adapter’s display list based on current expansion states.
     */
    fun refreshList(newItems: List<NavDrawerItem.ParentItem>) {
        this.recyclerList = newItems
        this.displayList = flattenDrawerItems(newItems)
         notifyDataSetChanged()
    }

    /**
     * Flattens a list of parent items into a single list of displayable items
     * including children (only if expanded).
     */
    fun flattenDrawerItems(items: List<NavDrawerItem.ParentItem>): List<NavDrawerItem> {
        val flatList = mutableListOf<NavDrawerItem>()
        for (item in items) {
            flatList.add(item)
            if (item.isExpanded) {
                flatList.addAll(item.children)
            }
        }
        return flatList
    }
}
