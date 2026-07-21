package org.commcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ItemConnectLearnModuleBinding

class ConnectLearnModuleAdapter(
    private val modules: List<ConnectLearnModuleSummaryRecord>,
) : RecyclerView.Adapter<ConnectLearnModuleAdapter.ModuleViewHolder>() {
    inner class ModuleViewHolder(
        val binding: ItemConnectLearnModuleBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ModuleViewHolder {
        val binding =
            ItemConnectLearnModuleBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return ModuleViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ModuleViewHolder,
        position: Int,
    ) {
        val module = modules[position]
        val context = holder.itemView.context
        with(holder.binding) {
            tvModuleName.text = "${position + 1}. ${module.name}"
            tvModuleEstimate.text =
                context.getString(R.string.connect_opportunity_module_estimated_time, module.timeEstimate)
        }
    }

    override fun getItemCount(): Int = modules.size
}
