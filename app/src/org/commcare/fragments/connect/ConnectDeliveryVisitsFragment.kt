package org.commcare.fragments.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import org.commcare.adapters.ConnectDeliveryPaymentUnitAdapter
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectDeliveryVisitsBinding
import org.commcare.fragments.RefreshableTab
import org.commcare.models.connect.ConnectDeliveryDetails

/**
 * Visits tab of a delivery opportunity: one card per payment unit, each opening that unit's visit
 * list.
 */
class ConnectDeliveryVisitsFragment :
    ConnectJobFragment<FragmentConnectDeliveryVisitsBinding>(),
    RefreshableTab {
    private val adapter = ConnectDeliveryPaymentUnitAdapter { navigateToDeliveries(it.unitUUID) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        binding.rvPaymentUnits.layoutManager = LinearLayoutManager(context)
        binding.rvPaymentUnits.adapter = adapter
        updateView()
        return view
    }

    override fun updateView() {
        reloadActiveJob()
        adapter.updateData(deliveryProgress())
    }

    private fun deliveryProgress(): List<ConnectDeliveryDetails> {
        val approvedCounts =
            job.deliveries
                .filter { APPROVED_STATUS.equals(it.status, ignoreCase = true) }
                .groupingBy { it.slugUUID }
                .eachCount()

        return job.paymentUnits.map { unit ->
            val approved = approvedCounts[unit.unitUUID] ?: 0
            ConnectDeliveryDetails(
                unitUUID = unit.unitUUID,
                deliveryName = unit.name,
                approvedCount = approved,
                pendingCount = unit.maxTotal - approved,
                totalAmount = job.getMoneyString(approved * unit.amount),
                remainingDays = job.daysRemaining,
                approvedPercentage = if (unit.maxTotal > 0) approved.toDouble() / unit.maxTotal * 100 else 0.0,
            )
        }
    }

    private fun navigateToDeliveries(unitUuid: String) {
        val navController = NavHostFragment.findNavController(this)
        if (navController.currentDestination?.id != R.id.connect_delivery_home_fragment) {
            return
        }
        navController.navigate(
            ConnectDeliveryHomeFragmentDirections
                .actionConnectDeliveryHomeFragmentToConnectDeliveryVisitsDetailFragment(unitUuid),
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentConnectDeliveryVisitsBinding = FragmentConnectDeliveryVisitsBinding.inflate(inflater, container, false)

    companion object {
        private const val APPROVED_STATUS = "approved"

        fun newInstance() = ConnectDeliveryVisitsFragment()
    }
}
