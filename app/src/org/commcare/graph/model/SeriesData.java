package org.commcare.graph.model;

import org.commcare.graph.model.XYPointData;

import java.util.Hashtable;
import java.util.Vector;

/*
 * Contains the fully-evaluated data for a single graph series.
 * @author jschweers
 */
public class SeriesData implements ConfigurableData {
    private Vector<XYPointData> mPoints;
    private Hashtable<String, String> mConfiguration;

    public SeriesData() {
        mPoints = new Vector<XYPointData>();
        mConfiguration = new Hashtable<String, String>();
    }

    public void addPoint(XYPointData p) {
        mPoints.addElement(p);
    }

    public Vector<XYPointData> getPoints() {
        return mPoints;
    }

    /*
     * Number of points in the series.
     */
    public int size() {
        return mPoints.size();
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.graph.model.ConfigurableData#setConfiguration(java.lang.String, java.lang.String)
     */

    public void setConfiguration(String key, String value) {
        mConfiguration.put(key, value);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.graph.model.ConfigurableData#getConfiguration(java.lang.String)
     */
    public String getConfiguration(String key) {
        return mConfiguration.get(key);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.graph.model.ConfigurableData#getConfiguration(java.lang.String, java.lang.String)
     */
    public String getConfiguration(String key, String defaultValue) {
        String value = getConfiguration(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }
}
