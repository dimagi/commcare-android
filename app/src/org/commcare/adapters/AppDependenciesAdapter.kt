package org.commcare.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.commcare.dalvik.R
import org.commcare.suite.model.AppDependency
import org.commcare.utils.StringUtils

class AppDependenciesAdapter(
    private val onInstallClick: (String) -> Unit
) : ListAdapter<AppDependency, AppDependenciesAdapter.ViewHolder>(AppDependencyDiffCallback) {

    class ViewHolder(
        itemView: View,
        val onInstallClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val titleTv: TextView = itemView.findViewById(R.id.title_tv)
        private val requiredTv: TextView = itemView.findViewById(R.id.required_tv)
        private val installBtn: Button = itemView.findViewById(R.id.install_button)
        private val checkMarkView: ImageView = itemView.findViewById(R.id.check_mark)
        private var currentDependency: AppDependency? = null

        init {
            installBtn.text = StringUtils.getStringRobust(itemView.context, R.string.app_dependency_install)
            installBtn.setOnClickListener {
                currentDependency?.let { dependency ->
                    onInstallClick(dependency.id)
                }
            }
        }

        fun bind(dependency: AppDependency) {
            currentDependency = dependency
            currentDependency!!.let {
                titleTv.text = it.name
                requiredTv.text = getRequiredText(it.isForce)
                installBtn.visibility = if (it.isInstalled) GONE else VISIBLE
                checkMarkView.visibility = if (it.isInstalled) VISIBLE else GONE
            }
        }

        private fun getRequiredText(forced: Boolean): CharSequence? {
            val stringResource = if (forced) R.string.app_dependency_required else R.string.app_dependency_optional
            return StringUtils.getStringRobust(itemView.context, stringResource)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appDependency = getItem(position)
        holder.bind(appDependency)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_depependency_list_item, parent, false)
        return ViewHolder(view, onInstallClick)
    }
}

object AppDependencyDiffCallback : DiffUtil.ItemCallback<AppDependency>() {
    override fun areItemsTheSame(oldItem: AppDependency, newItem: AppDependency): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: AppDependency, newItem: AppDependency): Boolean {
        return oldItem.id == newItem.id && oldItem.isInstalled == newItem.isInstalled
    }
}
