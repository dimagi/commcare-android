package org.commcare.android.models.graph;

/**
 * Representation of a point on an x, y plane.
 *
 * @author jschweers
 */
public class XYPointData {
    private String mX = null;
    private String mY = null;

    public XYPointData(String x, String y) {
        mX = x;
        mY = y;
    }

    public String getX() {
        return mX;
    }

    public String getY() {
        return mY;
    }

}
