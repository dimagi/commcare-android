package org.commcare.views.graph

import com.github.mikephil.charting.charts.BarLineChartBase

/**
 * @author $|-|!˅@M
 */
fun BarLineChartBase<*>.setXAxisLabels(labels: FloatArray) {
    setXAxisRenderer(
            GraphXAxisLabelRenderer(
                    viewPortHandler,
                    xAxis,
                    getTransformer(axisLeft.axisDependency),
                    labels,
                    true
            )
    )
}

fun BarLineChartBase<*>.setLeftYAxisLabels(labels: FloatArray) {
    rendererLeftYAxis = GraphYAxisLabelRenderer(
            viewPortHandler,
            axisLeft,
            getTransformer(axisLeft.axisDependency),
            labels
    )
}
