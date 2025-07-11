package org.commcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.commcare.android.database.connect.models.CredentialType
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ItemPersonalIdCredentialBinding
import org.commcare.utils.convertIsoDate

class PersonalIdCredentialAdapter(
    private val listener: OnCredentialClickListener
) : RecyclerView.Adapter<PersonalIdCredentialAdapter.CredentialViewHolder>() {

    private val credentialList = mutableListOf<PersonalIdCredential>()

    inner class CredentialViewHolder(val binding: ItemPersonalIdCredentialBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CredentialViewHolder {
        val binding = ItemPersonalIdCredentialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CredentialViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CredentialViewHolder, position: Int) {
        val item = credentialList[position]
        val formattedIssuedDate: String =
            convertIsoDate(item.issuedDate, "dd/MM/yyyy")

        with(holder.binding) {
            tvAppName.text = item.title
            tvIssuedDate.text = formattedIssuedDate
            tvUserActivity.text = item.level
            when (item.getCredentialType()) {
                CredentialType.LEARN -> {
                    view.setBackgroundColor(
                        ContextCompat.getColor(
                            root.context,
                            R.color.cc_dark_warm_accent_color
                        )
                    )
                    tvCredentialTitle.text =
                        root.context.getString(R.string.connect_certified_learner)
                }

                CredentialType.DELIVER -> {
                    view.setBackgroundColor(
                        ContextCompat.getColor(
                            root.context,
                            R.color.personal_id_delivery_app
                        )
                    )
                    tvCredentialTitle.text =
                        root.context.getString(R.string.connect_delivery_worker)
                }

                CredentialType.APP_ACTIVITY -> {
                    view.setBackgroundColor(
                        ContextCompat.getColor(
                            root.context,
                            R.color.connect_message_large_icon_color
                        )
                    )
                    tvCredentialTitle.text = root.context.getString(R.string.commcarehq_worker)
                }
            }

            tvViewCredential.setOnClickListener {
                listener.onViewCredentialClick(item)
            }
        }
    }

    override fun getItemCount(): Int = credentialList.size

    fun setData(newList: List<PersonalIdCredential>) {
        credentialList.clear()
        credentialList.addAll(newList)
        notifyDataSetChanged()
    }

    interface OnCredentialClickListener {
        fun onViewCredentialClick(credential: PersonalIdCredential)
    }
}
