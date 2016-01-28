package org.commcare.graph.model;

/**
 * Representation of a point on a bubble chart, which has an x, y position and an additional value for the bubble's radius.
 *
 * @author jschweers
 */
public class BubblePointData extends XYPointData {
    private String mRadius = null;

    public BubblePointData(String x, String y, String radius) {
        super(x, y);
        mRadius = radius;
    }

    public String getRadius() {
        return mRadius;
    }

}
