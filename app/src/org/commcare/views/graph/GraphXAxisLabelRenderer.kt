package org.commcare.views.graph

import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.ViewPortHandler


/**
 * Taken from https://github.com/PhilJay/MPAndroidChart/pull/2692#issuecomment-407209072
 * @author $|-|!˅@M
 */
class GraphXAxisLabelRenderer(
        viewPortHandler: ViewPortHandler,
        xAxis: XAxis,
        trans: Transformer,
        private val specificLabelPositions: FloatArray,
        private val adjustLabelCountToChartWidth: Boolean
) : XAxisRenderer(viewPortHandler, xAxis, trans) {

    override fun computeAxisValues(min: Float, max: Float) {

        mAxis.mEntryCount = specificLabelPositions.size
        mAxis.mEntries = specificLabelPositions
        mAxis.setCenterAxisLabels(false)

        computeSize()

        if (adjustLabelCountToChartWidth) {
            val width = mXAxis.mLabelRotatedWidth

            while (width * mAxis.mEntryCount > mViewPortHandler.chartWidth / 2f) {
                mAxis.mEntries = mAxis.mEntries.filterIndexed { index, fl -> index % 2 == 0 }.toFloatArray()
                mAxis.mEntryCount = mAxis.mEntries.size
            }
        }
    }
}
