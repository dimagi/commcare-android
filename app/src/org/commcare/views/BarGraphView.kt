package org.commcare.views

import android.content.Context
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import org.commcare.core.graph.model.GraphData


/**
 * @author $|-|!˅@M
 */
object BarGraphView {

    fun createBarGraph(context: Context, graphData: GraphData): BarChart {
        val barChart = BarChart(context)
        val entries = arrayListOf<BarEntry>()
        val labels = arrayListOf<String>()
        graphData.series[0].points.forEachIndexed { index, point ->
            entries.add(BarEntry(index.toFloat(), point.y.toFloat()))
            labels.add(point.x)
        }
        val barData = BarData(BarDataSet(entries, "x-axis"))
        barChart.data = barData
        barChart.xAxis.valueFormatter = object: ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return labels[value.toInt()]
            }
        }
        return barChart
    }
}
