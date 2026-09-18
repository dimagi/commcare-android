package org.commcare.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.commcare.connect.ConnectDateUtils.formatDate
import org.commcare.connect.database.ConnectJobUtils.isExpiryDateUnderFiveDays
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ConnectJobListItemBinding
import org.commcare.dalvik.databinding.ConnectJobListItemSectionHeaderBinding
import org.commcare.models.connect.ConnectJobListItem
import org.commcare.models.connect.ConnectLoginJobListModel
import java.text.DateFormat

class ConnectOpportunityListAdapter(
    inProgressJobs: List<ConnectLoginJobListModel>,
    newJobs: List<ConnectLoginJobListModel>,
    completedJobs: List<ConnectLoginJobListModel>,
    private val onJobClick: (ConnectLoginJobListModel) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val displayItems: List<ConnectJobListItem> =
        buildDisplayList(inProgressJobs, newJobs, completedJobs)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION_HEADER -> {
                SectionHeaderViewHolder(
                    ConnectJobListItemSectionHeaderBinding.inflate(inflater, parent, false),
                )
            }

            else -> {
                NonCorruptJobViewHolder(
                    ConnectJobListItemBinding.inflate(inflater, parent, false),
                )
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val displayItem = displayItems[position]) {
            is ConnectJobListItem.SectionHeader -> {
                (holder as SectionHeaderViewHolder).bind(displayItem.textResID)
            }

            is ConnectJobListItem.JobItem -> {
                (holder as NonCorruptJobViewHolder).bind(displayItem.jobModel, onJobClick)
            }
        }
    }

    override fun getItemCount(): Int = displayItems.size

    override fun getItemViewType(position: Int): Int =
        when (displayItems[position]) {
            is ConnectJobListItem.SectionHeader -> VIEW_TYPE_SECTION_HEADER
            is ConnectJobListItem.JobItem -> VIEW_TYPE_OPPORTUNITY
        }

    private fun buildDisplayList(
        inProgressJobs: List<ConnectLoginJobListModel>,
        newJobs: List<ConnectLoginJobListModel>,
        completedJobs: List<ConnectLoginJobListModel>,
    ): List<ConnectJobListItem> =
        buildList {
            addSection(R.string.connect_in_progress, inProgressJobs)
            addSection(R.string.connect_new_opportunities, newJobs)
            addSection(R.string.connect_completed_expired_label, completedJobs)
        }

    private fun MutableList<ConnectJobListItem>.addSection(
        @StringRes headerRes: Int,
        jobs: List<ConnectLoginJobListModel>,
    ) {
        if (jobs.isEmpty()) return
        add(ConnectJobListItem.SectionHeader(headerRes))
        jobs.forEach { add(ConnectJobListItem.JobItem(it)) }
    }

    private class NonCorruptJobViewHolder(
        private val binding: ConnectJobListItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: ConnectLoginJobListModel,
            onJobClick: (ConnectLoginJobListModel) -> Unit,
        ) {
            binding.root.tag = "opp_uuid: ${item.uuid}"
            binding.tvTitle.text = item.name

            bindDate(item)
            bindBadge(item)

            binding.root.setOnClickListener { onJobClick(item) }
        }

        private fun bindDate(item: ConnectLoginJobListModel) {
            val labelRes =
                when {
                    !item.jobFinished -> R.string.connect_label_expiry
                    item.userCompletedDelivery -> R.string.connect_label_completed_on
                    else -> R.string.connect_label_expired_on
                }
            binding.tvDateLabel.setText(labelRes)
            binding.tvDate.text = formatDate(item.date, DateFormat.SHORT)

            val expiringSoon = isExpiryDateUnderFiveDays(item.date)
            binding.tvDate.setTextColor(
                MaterialColors.getColor(
                    binding.root,
                    if (expiringSoon) R.attr.connectStatusNegative else R.attr.connectOnSurfaceVariant,
                ),
            )
            binding.ivInfo.visibility = if (expiringSoon) View.VISIBLE else View.GONE
        }

        private fun bindBadge(item: ConnectLoginJobListModel) {
            if (item.isNew) {
                showBadgeWithoutRing(R.drawable.ic_connect_new_opportunity)
                return
            }

            if (item.jobFinished) {
                showBadgeWithoutRing(
                    if (item.userCompletedDelivery) {
                        R.drawable.ic_connect_completed_badge
                    } else {
                        R.drawable.ic_connect_expired_badge
                    },
                )
                return
            }

            val progress: Int
            val progressColor: Int
            val iconRes: Int
            if (item.isLearningApp) {
                progress = item.learningProgress
                progressColor =
                    MaterialColors.getColor(
                        binding.root,
                        com.google.android.material.R.attr.colorPrimary,
                    )
                iconRes = R.drawable.ic_connect_learning
            } else {
                progress = item.deliveryProgress
                progressColor = MaterialColors.getColor(binding.root, R.attr.connectStatusPositive)
                iconRes = R.drawable.ic_connect_delivery
            }

            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.setStrokeWidth(
                binding.root.resources
                    .getDimensionPixelSize(R.dimen.connect_job_badge_ring_stroke)
                    .toFloat(),
            )
            binding.progressBar.setProgress(progress.toFloat())
            binding.progressBar.setProgressColor(progressColor)

            setBadgeIcon(iconRes, R.dimen.connect_job_badge_icon_in_ring)
        }

        private fun showBadgeWithoutRing(
            @DrawableRes iconRes: Int,
        ) {
            // INVISIBLE rather than GONE: the icon is centred on the ring, so the ring has to keep
            // occupying its box for the icon to stay put and for card heights to match across states.
            binding.progressBar.visibility = View.INVISIBLE
            setBadgeIcon(iconRes, R.dimen.connect_job_badge_icon_standalone)
        }

        private fun setBadgeIcon(
            @DrawableRes iconRes: Int,
            @DimenRes sizeRes: Int,
        ) {
            val size = binding.root.resources.getDimensionPixelSize(sizeRes)
            binding.imgJobType.layoutParams =
                binding.imgJobType.layoutParams.apply {
                    width = size
                    height = size
                }
            binding.imgJobType.setImageResource(iconRes)
        }
    }

    private class SectionHeaderViewHolder(
        private val binding: ConnectJobListItemSectionHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            @StringRes headerTextResId: Int,
        ) {
            binding.tvSectionHeader.setText(headerTextResId)
        }
    }

    companion object {
        private const val VIEW_TYPE_SECTION_HEADER = 0
        private const val VIEW_TYPE_OPPORTUNITY = 1
    }
}
