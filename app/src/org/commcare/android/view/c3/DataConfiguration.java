package org.commcare.android.view.c3;

import org.commcare.android.util.InvalidStateException;
import org.commcare.suite.model.graph.AnnotationData;
import org.commcare.suite.model.graph.BubblePointData;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.GraphData;
import org.commcare.suite.model.graph.SeriesData;
import org.commcare.suite.model.graph.XYPointData;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;

/**
 * Created by jschweers on 11/16/2015.
 */
public class DataConfiguration extends Configuration {
    // Actual data: array of arrays, where first element is a string id
    // and later elements are data, either x values or y values.
    private JSONArray mColumns = new JSONArray();

    // Hash that pairs up the arrays defined in columns,
    // y-values-array-id => x-values-array-id
    private JSONObject mXs = new JSONObject();

    // Hash of y-values id => name for legend
    JSONObject mNames = new JSONObject();

    // Hash of y-values id => 'y' or 'y2' depending on whether this data
    // should be plotted against the primary or secondary y axis
    private JSONObject mAxes = new JSONObject();

    // Hash of y-values id => line, scatter, bar, area, etc.
    JSONObject mTypes = new JSONObject();

    // Hash of y-values id => series color
    JSONObject mColors = new JSONObject();

    public DataConfiguration(GraphData data) throws JSONException, InvalidStateException {
        super(data);

        // Bubble graph data:
        //  y-values id => array of radius values
        //  y-values id => max radius found in that data (or specified by max-radius param)
        JSONObject radii = new JSONObject();
        JSONObject maxRadii = new JSONObject();

        // Bar graph data
        int barCount = 0;
        JSONArray barLabels = new JSONArray();
        barLabels.put("");

        int seriesIndex = 0;
        boolean addingBarLabels = false;
        boolean addedBarLabels = false;
        for (SeriesData s : mData.getSeries()) {
            JSONArray xValues = new JSONArray();
            JSONArray yValues = new JSONArray();

            String xID = "x" + seriesIndex;
            String yID = "y" + seriesIndex;
            mXs.put(yID, xID);

            String type = "line";
            // TODO: allow customizing fill's color
            // TODO: remove fill-above from docs, see if anone's using it
            // TODO: support point-style: s.getConfiguration("point-style", "circle").toLowerCase()
            if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
                type = "scatter";
            } else if (mData.getType().equals(Graph.TYPE_BAR)) {
                type = "bar";
                if (!addedBarLabels) {
                    addingBarLabels = true;
                    addedBarLabels = true;
                } else {
                    addingBarLabels = false;
                }
            } else if (s.getConfiguration("fill-below") != null) {
                type = "area";
            }
            mTypes.put(yID, type);

            xValues.put(xID);
            yValues.put(yID);

            Vector<XYPointData> sortedPoints = new Vector<XYPointData>(s.size());
            sortedPoints.addAll(s.getPoints());
            if (Graph.TYPE_BAR.equals(mData.getType())) {
                String barSort = s.getConfiguration("bar-sort");
                if (barSort != null) {
                    Comparator<XYPointData> comparator;
                    if (barSort.equalsIgnoreCase("ascending")) {
                        comparator = new AscendingValuePointComparator();
                    } else if (barSort.equalsIgnoreCase("descending")) {
                        comparator = new DescendingValuePointComparator();
                    } else {
                        comparator = new StringPointComparator();
                    }
                    Collections.sort(sortedPoints, comparator);
                }
            }

            int pointIndex = 0;
            JSONArray rValues = new JSONArray();
            double maxRadius = parseDouble(s.getConfiguration("max-radius", "0"), "max-radius");
            for (XYPointData p : sortedPoints) {
                String description = "point (" + p.getX() + ", " + p.getY() + ")";
                if (mData.getType().equals(Graph.TYPE_BAR)) {
                    // In CommCare, bar graphs are specified with x as a set of text labels
                    // and y as a set of values. In C3, bar graphs are still basically
                    // of XY graphs, with numeric x and y values. Deal with this by
                    // assigning an arbitrary, evenly-spaced x value to each bar and then
                    // using the user's x values as custom labels.
                    xValues.put(pointIndex + 1);
                    barCount = Math.max(barCount, pointIndex + 1);
                    if (addingBarLabels) {
                        barLabels.put(p.getX());
                    }
                } else {
                    xValues.put(parseXValue(p.getX(), description));
                }
                yValues.put(parseYValue(p.getY(), description));
                if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
                    BubblePointData b = (BubblePointData)p;
                    double r = parseRadiusValue(b.getRadius(), description + " with radius " + b.getRadius());
                    rValues.put(r);
                    maxRadius = Math.max(maxRadius, r);
                }
                pointIndex++;
            }
            mColumns.put(xValues);
            mColumns.put(yValues);
            if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
                radii.put(yID, rValues);
                maxRadii.put(yID, maxRadius);
            }

            String name = s.getConfiguration("name", "");
            if (name != null) {
                mNames.put(yID, name);
            }

            String color = s.getConfiguration("line-color", "#ff000000");
            // TODO: Handle transparency (and test on bubble graphs)
            if (color.length() == "#aarrggbb".length()) {
                color = "#" + color.substring(3);
            }
            mColors.put(yID, color);

            mAxes.put(yID, Boolean.valueOf(s.getConfiguration("secondary-y", "false")).equals(Boolean.TRUE) ? "y2" : "y");

            seriesIndex++;
        }

        if (mData.getType().equals(Graph.TYPE_BAR)) {
            mData.setConfiguration("x-min", "0.5");
            mData.setConfiguration("x-max", String.valueOf(barCount + 0.5));
            barLabels.put("");
            mVariables.put("barLabels", barLabels.toString());
        }

        addBoundaries();

        addAnnotations();

        mConfiguration.put("axes", mAxes);
        mConfiguration.put("colors", mColors);
        mConfiguration.put("columns", mColumns);
        mConfiguration.put("names", mNames);
        mConfiguration.put("types", mTypes);
        mConfiguration.put("xs", mXs);

        if (mData.getType().equals(Graph.TYPE_BAR) && Boolean.valueOf(mData.getConfiguration("stack", "false")).equals(Boolean.TRUE)) {
            JSONArray inner = new JSONArray();
            for (Iterator<String> i = mTypes.keys(); i.hasNext();) {
                String key = i.next();
                if (mTypes.get(key).equals("bar")) {
                    inner.put(key);
                }
            }
            JSONArray outer = new JSONArray();
            outer.put(inner);
            mConfiguration.put("groups", outer);
        }

        mVariables.put("radii", radii.toString());
        mVariables.put("maxRadii", maxRadii.toString());
    }

    private void addAnnotations() throws JSONException, InvalidStateException {
        String xID = "annotationsX";
        String yID = "annotationsY";

        mXs.put(yID, xID);
        mTypes.put(yID, "line");
        mAxes.put(yID, "y");

        JSONArray xValues = new JSONArray();
        xValues.put(xID);
        JSONArray yValues = new JSONArray();
        yValues.put(yID);
        JSONArray text = new JSONArray();

        // TODO: This is broken. Need to add one series per text, because C3 re-orders the data.
        for (AnnotationData a : mData.getAnnotations()) {
            String description = "annotation '" + text + "' at (" + a.getX() + ", " + a.getY() + ")";
            xValues.put(parseXValue(a.getX(), description));
            yValues.put(parseYValue(a.getY(), description));
            text.put(a.getAnnotation());
        }

        mColumns.put(xValues);
        mColumns.put(yValues);
        mVariables.put("annotations", text.toString());
    }

    private void addBoundaries() throws JSONException, InvalidStateException {
        String xMin = mData.getConfiguration("x-min");
        String xMax = mData.getConfiguration("x-max");

        if (xMin == null || xMax == null) {
            return;
        }

        String xID = "boundsX";
        JSONArray xValues = new JSONArray();
        xValues.put(xID);
        xValues.put(parseXValue(xMin, "x-min"));
        xValues.put(parseXValue(xMax, "x-max"));
        boolean shouldAddX = false;

        String yMin = mData.getConfiguration("y-min");
        String yMax = mData.getConfiguration("y-max");
        if (yMin != null && yMax != null) {
            shouldAddX = true;
            String yID = "boundsY";
            mXs.put(yID, xID);
            mTypes.put(yID, "line");
            mAxes.put(yID, "y");

            JSONArray yValues = new JSONArray();
            yValues.put(yID);
            yValues.put(parseYValue(yMin, "y-min"));
            yValues.put(parseYValue(yMax, "y-max"));
            mColumns.put(yValues);
        }

        // Secondary y axis
        String y2Min = mData.getConfiguration("secondary-y-min");
        String y2Max = mData.getConfiguration("secondary-y-max");
        if (y2Min != null && y2Max != null) {
            shouldAddX = true;
            String y2ID = "boundsY2";
            mXs.put(y2ID, xID);
            mTypes.put(y2ID, "line");
            mAxes.put(y2ID, "y2");

            JSONArray y2Values = new JSONArray();
            y2Values.put(y2ID);
            y2Values.put(y2Min);
            y2Values.put(y2Max);
            mColumns.put(y2Values);
        }

        if (shouldAddX) {
            mColumns.put(xValues);
        }
    }

    /**
     * Comparator to sort XYPointData-derived objects by x value without parsing them.
     * Useful for bar graphs, where x values are text.
     *
     * @author jschweers
     */
    private class StringPointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            return lhs.getX().compareTo(rhs.getX());
        }
    }

    /**
     * Comparator to sort XYPoint-derived data by y value, in ascending order.
     * Useful for bar graphs, nonsensical for other graphs.
     *
     * @author jschweers
     */
    private class AscendingValuePointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            try {
                return Double.valueOf(parseXValue(lhs.getY(), "")).compareTo(Double.valueOf(parseXValue(rhs.getY(), "")));
            } catch (InvalidStateException e) {
                return 0;
            }
        }
    }

    /**
     * Comparator to sort XYPoint-derived data by y value, in descending order.
     * Useful for bar graphs, nonsensical for other graphs.
     *
     * @author jschweers
     */
    private class DescendingValuePointComparator implements Comparator<XYPointData> {
        @Override
        public int compare(XYPointData lhs, XYPointData rhs) {
            try {
                return Double.valueOf(parseXValue(rhs.getY(), "")).compareTo(Double.valueOf(parseXValue(lhs.getY(), "")));
            } catch (InvalidStateException e) {
                return 0;
            }
        }
    }
}
