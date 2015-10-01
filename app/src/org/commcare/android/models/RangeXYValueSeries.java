package org.commcare.android.models;

import org.achartengine.model.XYValueSeries;

/**
 * Subclass of AChartEngine's XYValueSeries allowing user to set a maximum radius.
 * Useful when creating multiple bubble charts (or series on the same chart)
 * and wanting their bubbles to be on the same scale.
 *
 * @author jschweers
 */
public class RangeXYValueSeries extends XYValueSeries {
    private Double max = null;

    public RangeXYValueSeries(String title) {
        super(title);
    }

    @Override
    public double getMaxValue() {
        return max == null ? super.getMaxValue() : max;
    }

    /*
     * Set largest desired radius. No guarantees on what happens if the data
     * actually contains a larger value.
     */
    public void setMaxValue(double value) {
        max = value;
    }
}
