package org.commcare.fragments.connect

import androidx.fragment.app.Fragment
import org.commcare.dalvik.R
import org.commcare.fragments.RefreshableTab

class ConnectDeliveryVisitsFragment :
    Fragment(R.layout.fragment_connect_delivery_visits),
    RefreshableTab {
    override fun updateView() {
    }

    companion object {
        fun newInstance() = ConnectDeliveryVisitsFragment()
    }
}
