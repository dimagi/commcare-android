package org.commcare.views.graph

import android.graphics.Canvas
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.renderer.YAxisRenderer
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.ViewPortHandler

/**
 * Taken from https://github.com/PhilJay/MPAndroidChart/pull/2692#issuecomment-407209072
 * @author $|-|!˅@M
 */
class GraphYAxisLabelRenderer(
        viewPortHandler: ViewPortHandler,
        xAxis: YAxis,
        trans: Transformer,
        private val labels: FloatArray
) : YAxisRenderer(viewPortHandler, xAxis, trans) {

    override fun drawYLabels(c: Canvas?, fixedPosition: Float, positions: FloatArray?, offset: Float) {

        val specificPositions = FloatArray(labels.size * 2)
        for (i in labels.indices) {
            specificPositions[i * 2 + 1] = labels[i]
        }
        mTrans.pointValuesToPixel(specificPositions)
        for (i in labels.indices) {
            val y = specificPositions[i * 2 + 1]
            if (mViewPortHandler.isInBoundsY(y)) {
                val text = mYAxis.valueFormatter.getFormattedValue(labels[i])
                c?.drawText(text, fixedPosition, y + offset, mAxisLabelPaint)
            }
        }
        return
    }
}



