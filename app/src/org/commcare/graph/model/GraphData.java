package org.commcare.graph.model;

import java.util.Hashtable;
import java.util.Vector;

/**
 * Contains all of the fully-evaluated data to draw a graph: a type, set of series, set of text annotations, and key-value map of configuration.
 *
 * @author jschweers
 */
public class GraphData implements ConfigurableData {
    private String mType;
    private Vector<SeriesData> mSeries;
    private Hashtable<String, String> mConfiguration;
    private Vector<AnnotationData> mAnnotations;

    public GraphData() {
        mSeries = new Vector<>();
        mConfiguration = new Hashtable<>();
        mAnnotations = new Vector<>();
    }

    public String getType() {
        return mType;
    }

    public void setType(String type) {
        mType = type;
    }

    public Vector<SeriesData> getSeries() {
        return mSeries;
    }

    public void addSeries(SeriesData s) {
        mSeries.addElement(s);
    }

    public void addAnnotation(AnnotationData a) {
        mAnnotations.addElement(a);
    }

    public Vector<AnnotationData> getAnnotations() {
        return mAnnotations;
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
