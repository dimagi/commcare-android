package org.commcare.navdrawer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.commcare.dalvik.R

/**
 * Adapter to manage the list of navigation drawer items in a RecyclerView.
 *
 * @param context The context for inflating layouts
 * @param recyclerList List of drawer items to display
 * @param onItemClick Callback invoked when an item is clicked
 */
class NavDrawerAdapter(
    private val context: Context,
    private var recyclerList: List<NavDrawerItem>,
    private val onItemClick: (NavDrawerItem) -> Unit,
) : RecyclerView.Adapter<NavDrawerAdapter.NavDrawerViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): NavDrawerViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.nav_drawer_list_item, parent, false)
        return NavDrawerViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: NavDrawerViewHolder,
        position: Int,
    ) {
        holder.bind(recyclerList[position])
    }

    override fun getItemCount(): Int = recyclerList.size

    inner class NavDrawerViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.list_title)
        private val icon = itemView.findViewById<ImageView>(R.id.list_icon)
        private val messageCount = itemView.findViewById<TextView>(R.id.tv_message_count)
        private val flMessageCounter = itemView.findViewById<FrameLayout>(R.id.badge_layout)

        fun bind(item: NavDrawerItem) {
            title.text = item.title
            icon.setImageResource(item.iconResId)
            bindBadgeCount(item.badgeCount)
            itemView.setOnClickListener { onItemClick(item) }
        }

        private fun bindBadgeCount(count: Int?) {
            val countText =
                count?.let {
                    if (it > 9) "9+" else it.toString()
                }

            if (!countText.isNullOrEmpty()) {
                messageCount.text = countText
                flMessageCounter.visibility = View.VISIBLE
            } else {
                flMessageCounter.visibility = View.GONE
            }
        }
    }

    /**
     * Refreshes the adapter's list of drawer items.
     */
    fun refreshList(newItems: List<NavDrawerItem>) {
        this.recyclerList = newItems
        notifyDataSetChanged()
    }
}
