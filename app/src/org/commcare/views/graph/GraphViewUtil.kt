package org.commcare.views.graph

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import org.commcare.core.graph.model.GraphData
import org.commcare.core.graph.util.GraphUtil
import org.commcare.utils.toFloatArray
import org.commcare.utils.toFloatKeyAndStringValueArray
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.max


/**
 * @author $|-|!˅@M
 */
object GraphViewUtil {

    fun createGraph(context: Context, graphData: GraphData) : View {
        return when (graphData.type) {
            GraphUtil.TYPE_BAR -> {
                createBarGraph(context, graphData)
            }
            GraphUtil.TYPE_BUBBLE -> {
                createBarGraph(context, graphData)
            }
            GraphUtil.TYPE_TIME -> {
                createBarGraph(context, graphData)
            }
            GraphUtil.TYPE_XY -> {
                createXYGraph(context, graphData)
            }
            else -> throw RuntimeException("Invalid graph type : " + graphData.type)
        }
    }

    private fun createXYGraph(context: Context, graphData: GraphData): LineChart {
        val lineChart = LineChart(context)
        val dataSet = arrayListOf<LineDataSet>()
        graphData.series.forEachIndexed { index, series ->
            val entries = arrayListOf<Entry>()
            series.points.forEach { point ->
                entries.add(Entry(point.x.toFloat(), point.y.toFloat()))
            }
            val ds = LineDataSet(entries, "--$index-- lab")

            series.getConfiguration(FILL_ABOVE)?.let {
                ds.setDrawFilled(true)
                ds.fillColor = Color.parseColor(it)
                ds.setFillFormatter { dataSet, dataProvider ->
                    val data = dataProvider.lineData
                    max(dataProvider.yChartMax, max(data.yMax, dataSet.yMax))
                }
            }
            series.getConfiguration(FILL_BELOW)?.let {
                ds.setDrawFilled(true)
                ds.fillColor = Color.parseColor(it)
            }
            dataSet.add(ds)
        }

        lineChart.data = LineData(dataSet.toList())
        setAxisMinMax(lineChart, graphData)
        setAxisLabels(lineChart, graphData)
        return lineChart
    }

    private fun createBarGraph(context: Context, graphData: GraphData): BarChart {
        val barChart = BarChart(context)
        val entries = arrayListOf<BarEntry>()
        val labels = arrayListOf<String>()
        graphData.series[0].points.forEachIndexed { index, point ->
            entries.add(BarEntry(index.toFloat(), point.y.toFloat()))
            labels.add(point.x)
        }
        val barData = BarData(BarDataSet(entries, "x-axis"))
        barChart.data = barData

        // Configure axis
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisRight.isEnabled = false
        barChart.xAxis.labelRotationAngle = 315f
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.setDrawGridLines(false)
        barChart.xAxis.valueFormatter = object: ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return labels[value.toInt()]
            }
        }
        barChart.xAxis.granularity = 1f
        return barChart
    }

    // <T : ChartData<out IDataSet<out Entry?>?>?>

    private fun setAxisMinMax(chart: BarLineChartBase<LineData>, graphData: GraphData) {
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.axisRight.isEnabled = false
        chart.xAxis.labelRotationAngle = 315f
        chart.xAxis.setDrawGridLines(false)

        graphData.getConfiguration(X_MIN)?.let {
            chart.xAxis.axisMinimum = it.toFloat()
        }
        graphData.getConfiguration(X_MAX)?.let {
            chart.xAxis.axisMaximum = it.toFloat()
        }
        graphData.getConfiguration(Y_MIN)?.let {
            chart.axisLeft.axisMinimum = it.toFloat()
        }
        graphData.getConfiguration(Y_MAX)?.let {
            chart.axisLeft.axisMaximum = it.toFloat()
        }
    }

    private fun setAxisLabels(chart: BarLineChartBase<LineData>, graphData: GraphData) {
        // Labels can be array, object or single point
        // [1, 3, 5]
        // { "0": "freezing", "100": "boiling" }
        // 3
        graphData.getConfiguration(X_LABEL)?.let {
            setAxisLabels(chart, it, true)
        } ?: run {
            chart.xAxis.granularity = 1f
        }

        graphData.getConfiguration(Y_LABEL)?.let {
            setAxisLabels(chart, it, false)
        } ?: run {
            chart.axisLeft.granularity = 1f
        }
    }

    private fun setAxisLabels(chart: BarLineChartBase<LineData>, config: String, isXAxis: Boolean) {
        try {
            val arr = JSONArray(config)
            val labels = arr.toFloatArray()
            if (isXAxis) {
                chart.setXAxisLabels(labels)
            } else {
                chart.setLeftYAxisLabels(labels)
            }
        } catch (e: JSONException) {
            try {
                val obj = JSONObject(config)
                val (labelPos, labelTexts) = obj.toFloatKeyAndStringValueArray()
                if (isXAxis) {
                    chart.setXAxisLabels(labelPos)
                    chart.xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return labelTexts[value] as String
                        }
                    }
                } else {
                    chart.setLeftYAxisLabels(labelPos)
                    chart.axisLeft.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return labelTexts[value] as String
                        }
                    }
                }
            } catch (je: JSONException) {
                if (isXAxis) {
                    chart.xAxis.labelCount = config.toInt()
                } else {
                    chart.axisLeft.labelCount = config.toInt()
                }
            }
        }
    }

    const val X_MIN = "x-min"
    const val X_MAX = "x-max"
    const val Y_MIN = "y-min"
    const val Y_MAX = "y-max"
    const val SECONDARY_Y_MIN = "secondary-y-min"
    const val SECONDARY_Y_MAX = "secondary-y-max"
    const val X_TITLE = "x-title" // fetal heart rate
    const val Y_TITLE = "y-title" // hours
    const val SECONDARY_Y_TITLE = "secondary-y-title"
    const val X_LABEL = "x-labels"
    const val Y_LABEL = "y-labels"
    const val SECONDARY_Y_LABEL = "secondary-y-labels"

    const val BAR_ORIENTATION = "bar-orientation"

    // line config
    const val LINE_COLOR = "line-color"
    const val FILL_ABOVE = "fill-above"
    const val FILL_BELOW = "fill-below"
}

