package org.commcare.fragments.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import org.commcare.connect.ConnectDateUtils
import org.commcare.connect.ConnectMoneyUtils
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.FragmentConnectDeliveryDashboardBinding
import org.commcare.fragments.RefreshableTab
import org.commcare.views.connect.ConnectInfoHalfCard
import org.commcare.views.connect.ConnectProgressCard
import java.text.DateFormat

/**
 * Dashboard tab of a delivery opportunity: visit progress and a per-payment-unit breakdown of the
 * worker's own progress.
 *
 * Figures render disabled once no further work earns progress, and an individual payment unit's card
 * also dims on its own once that unit is out of visits.
 */
class ConnectDeliveryDashboardFragment :
    ConnectJobFragment<FragmentConnectDeliveryDashboardBinding>(),
    RefreshableTab {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        updateView()
        return view
    }

    override fun updateView() {
        val contentEnabled = !job.isFurtherWorkBlocked
        bindHeader()
        bindVisitProgress(contentEnabled)
        bindProgressGrid(contentEnabled)
    }

    private fun bindHeader() {
        binding.deliveryJobTitle.text = job.title
        binding.deliveryExpiryValue.text =
            ConnectDateUtils.formatDate(job.projectEndDate, DateFormat.SHORT)
    }

    private fun bindVisitProgress(contentEnabled: Boolean) {
        val doneToday = job.numberOfDeliveriesToday()
        val remainingToday = (job.maxDailyVisits - doneToday).coerceAtLeast(0)
        val cardMessage: String? = job.getCardMessageText(requireContext())
        binding.deliveryProgressCard.bind(
            ConnectProgressCard.State(
                title = getString(R.string.connect_delivery_visit_progress_title),
                contentEnabled = contentEnabled,
                info =
                    cardMessage?.let { message ->
                        ConnectProgressCard.State.Info(
                            message = message,
                            appearance = ConnectProgressCard.State.Info.Appearance.WARNING,
                        )
                    },
                semiCircle =
                    ConnectProgressCard.State.SemiCircle(
                        current = job.completedVisits,
                        max = job.maxVisits,
                        description = getString(R.string.connect_delivery_total_visits_completed),
                    ),
                linearProgress =
                    ConnectProgressCard.State.LinearProgress(
                        label = getString(R.string.connect_delivery_daily_visits),
                        current = doneToday,
                        max = job.maxDailyVisits,
                        caption =
                            resources.getQuantityString(
                                R.plurals.connect_delivery_visits_remaining_today,
                                remainingToday,
                                remainingToday,
                            ),
                    ),
            ),
        )
    }

    private fun bindProgressGrid(contentEnabled: Boolean) {
        val grid = binding.deliveryProgressGrid
        grid.removeAllViews()

        val counts = job.getDeliveryCountsPerPaymentUnit(false)
        val unitsAtLimit = job.paymentUnitsAtLimit
        job.paymentUnits.forEach { unit ->
            val card =
                halfCard(
                    value = (counts[unit.unitUUID] ?: 0).toString(),
                    title = unit.name,
                    subtitle =
                        getString(
                            R.string.connect_delivery_amount_each,
                            ConnectMoneyUtils.moneyStringWithSymbol(job.currency, unit.amount),
                        ),
                    iconRes = R.drawable.ic_connect_footprint,
                    contentEnabled = contentEnabled && !unitsAtLimit.contains(unit.unitUUID),
                )
            grid.addView(card, cellParams(grid.childCount))
        }

        val earnings =
            halfCard(
                value = ConnectMoneyUtils.moneyStringWithSymbol(job.currency, job.paymentAccrued),
                title = getString(R.string.connect_delivery_total_earnings),
                subtitle = null,
                iconRes = R.drawable.ic_connect_savings,
                contentEnabled = contentEnabled,
            )
        grid.addView(earnings, cellParams(grid.childCount))
    }

    private fun halfCard(
        value: CharSequence,
        title: CharSequence,
        subtitle: CharSequence?,
        @DrawableRes iconRes: Int,
        contentEnabled: Boolean,
    ) = ConnectInfoHalfCard(requireContext()).apply {
        valueText = value
        titleText = title
        subtitleText = subtitle
        icon = ContextCompat.getDrawable(context, iconRes)
        this.contentEnabled = contentEnabled
    }

    /**
     * Neighbouring cards each contribute half the gap horizontally, so the row gap is doubled from
     * the same dimension to keep the spacing square whatever the display density rounds to.
     */
    private fun cellParams(index: Int) =
        GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(index % GRID_COLUMNS, 1, 1f)
            rowSpec = GridLayout.spec(index / GRID_COLUMNS, 1, GridLayout.FILL)
            val halfGutter = resources.getDimensionPixelSize(R.dimen.connect_grid_gutter_half)
            if (index % GRID_COLUMNS == 0) marginEnd = halfGutter else marginStart = halfGutter
            bottomMargin = halfGutter * 2
        }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentConnectDeliveryDashboardBinding = FragmentConnectDeliveryDashboardBinding.inflate(inflater, container, false)

    companion object {
        private const val GRID_COLUMNS = 2

        @JvmStatic
        fun newInstance() = ConnectDeliveryDashboardFragment()
    }
}
