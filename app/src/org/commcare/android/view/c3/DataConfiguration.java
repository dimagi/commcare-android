package org.commcare.android.view.c3;

import org.commcare.android.util.InvalidStateException;
import org.commcare.suite.model.graph.AnnotationData;
import org.commcare.suite.model.graph.BubblePointData;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.GraphData;
import org.commcare.suite.model.graph.SeriesData;
import org.commcare.suite.model.graph.XYPointData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 * Data-related configuration for C3. This configuration should be run before
 * any others, as th data will sometimes affect other configuration.
 *
 * Created by jschweers on 11/16/2015.
 */
public class DataConfiguration extends Configuration {
    // Actual data: array of arrays, where first element is a string id
    // and later elements are data, either x values or y values.
    private final JSONArray mColumns = new JSONArray();

    // Hash that pairs up the arrays defined in columns,
    // y-values-array-id => x-values-array-id
    private final JSONObject mXs = new JSONObject();

    // Hash of y-values id => name for legend
    private final JSONObject mNames = new JSONObject();

    // Hash of y-values id => 'y' or 'y2' depending on whether this data
    // should be plotted against the primary or secondary y axis
    private final JSONObject mAxes = new JSONObject();

    // Hash of y-values id => line, scatter, bar, area, etc.
    private final JSONObject mTypes = new JSONObject();

    // Hash of y-values id => series color
    private final JSONObject mColors = new JSONObject();

    // Hash of y-values id => point-style string ("circle", "none", "cross", etc.)
    // Doubles as a record of all user-defined series
    // (as opposed to series for annotations, etc.)
    private final JSONObject mPointStyles = new JSONObject();

    // Bar graph data:
    //  barCount: for the sake of setting x min and max
    //  barLabels: the actual labels to display, which are supposed to be the same
    //      for every series, hence the booleans so we only record them once
    private int mBarCount = 0;
    private final JSONArray mBarLabels = new JSONArray("['']");

    // Bubble graph data:
    //  y-values id => array of radius values
    //  y-values id => max radius found in that data (or specified by max-radius param)
    JSONObject mRadii = new JSONObject();
    JSONObject mMaxRadii = new JSONObject();

    public DataConfiguration(GraphData data) throws JSONException, InvalidStateException {
        super(data);

        // Process data for each series
        int seriesIndex = 0;
        for (SeriesData s : mData.getSeries()) {
            String xID = "x" + seriesIndex;
            String yID = "y" + seriesIndex;
            mXs.put(yID, xID);

            setColumns(xID, yID, s);
            setName(yID, s);
            setColor(yID, s);
            setPointStyle(yID, s);
            setType(yID, s);
            setYAxis(yID, s);

            seriesIndex++;
        }

        // Set up separate variables for features that C3 doesn't support well
        mVariables.put("radii", mRadii.toString());
        mVariables.put("maxRadii", mMaxRadii.toString());
        mVariables.put("pointStyles", mPointStyles.toString());

        // Data-based tweaking of user's configuration and adding system series
        normalizeBoundaries();
        addAnnotations();
        addBoundaries();

        // Finally, apply all data to main configuration
        mConfiguration.put("axes", mAxes);
        mConfiguration.put("colors", mColors);
        mConfiguration.put("columns", mColumns);
        mConfiguration.put("names", mNames);
        mConfiguration.put("types", mTypes);
        mConfiguration.put("xs", mXs);
        mConfiguration.put("groups", getGroups());
    }

    /**
     * Add annotations, by creating a fake series with data labels turned on.
     */
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

    /**
     * Create fake series so there's data all the way to the edges of the user-specified
     * min and max. C3 does tick placement in part based on data, so this will force
     * it to place ticks based on the user's desired min/max range.
     */
    private void addBoundaries() throws JSONException, InvalidStateException {
        String xMin = mData.getConfiguration("x-min");
        String xMax = mData.getConfiguration("x-max");

        // If we don't have user-specified bounds, don't bother.
        if (xMin == null || xMax == null) {
            return;
        }

        String xID = "boundsX";
        if (addBoundary(xID, "boundsY", "y") || addBoundary(xID, "boundsY2", "secondary-y")) {
            // If at least one y axis had boundaries and therefore a series was created,
            // now create the matchin x values
            JSONArray xValues = new JSONArray();
            xValues.put(xID);
            xValues.put(parseXValue(xMin, "x-min"));
            xValues.put(parseXValue(xMax, "x-max"));
            mColumns.put(xValues);
        }
    }

    /**
     * Helper for addBoundaries: possibly add a series for either the primary or secondary y axis,
     * depending on whether or not that axis has a min and max specified.
     * @param xID ID of x column to associate with the new series
     * @param yID ID of y column for new series
     * @param prefix "y" or "secondary-y"
     * @return True iff a series was actually created
     */
    private boolean addBoundary(String xID, String yID, String prefix) throws JSONException, InvalidStateException {
        String min = mData.getConfiguration(prefix + "-min");
        String max = mData.getConfiguration(prefix + "-max");
        if (min != null && max != null) {
            mXs.put(yID, xID);
            mTypes.put(yID, "line");
            mAxes.put(yID, "y2");

            JSONArray yValues = new JSONArray();
            yValues.put(yID);
            yValues.put(parseYValue(min, "secondary-y-min"));
            yValues.put(parseYValue(max, "secondary-y-max"));
            mColumns.put(yValues);
            return true;
        }
        return false;
    }

    /**
     * Set up stacked bar graph, if needed.
     * @return JSONArray of configuration for groups, C3's version of stacking
     */
    private JSONArray getGroups() throws JSONException {
        JSONArray outer = new JSONArray();
        if (mData.getType().equals(Graph.TYPE_BAR)
                && Boolean.valueOf(mData.getConfiguration("stack", "false"))) {
            JSONArray inner = new JSONArray();
            for (Iterator<String> i = mTypes.keys(); i.hasNext();) {
                String key = i.next();
                if (mTypes.get(key).equals("bar")) {
                    inner.put(key);
                }
            }
            outer.put(inner);
        }
        return outer;
    }

    /**
     * Fetch all points associated with a given series. Bar charts will have their points sorted:
     * - By value if the user specified bar-sort (note this is nonsensical for multi-series bar charts)
     * - Otherwise, alphabetically by label
     * @param s
     */
    private List<XYPointData> getPoints(SeriesData s) {
        Vector<XYPointData> points = new Vector<>(s.size());
        points.addAll(s.getPoints());
        if (Graph.TYPE_BAR.equals(mData.getType())) {
            String barSort = s.getConfiguration("bar-sort");
            Comparator<XYPointData> comparator = new StringPointComparator();
            if (barSort != null) {
                if (barSort.equalsIgnoreCase("ascending")) {
                    comparator = new AscendingValuePointComparator();
                } else if (barSort.equalsIgnoreCase("descending")) {
                    comparator = new DescendingValuePointComparator();
                }
            }
            Collections.sort(points, comparator);
        }

        return points;
    }

    /**
     * For bar charts, set up bar labels and force the x axis min and max so bars are spaced nicely
     */
    private void normalizeBoundaries() {
        if (mData.getType().equals(Graph.TYPE_BAR)) {
            mData.setConfiguration("x-min", "0.5");
            mData.setConfiguration("x-max", String.valueOf(mBarCount + 0.5));
            mBarLabels.put("");
            mVariables.put("barLabels", mBarLabels.toString());
        }
    }

    /**
     * Set color for a given series.
     * @param yID ID of y-values array to set color
     * @param s SeriesData from which to pull color
     */
    private void setColor(String yID, SeriesData s) throws JSONException {
        // TODO: Handle transparency (and test on bubble graphs)
        String color = s.getConfiguration("line-color", "#ff000000");
        if (color.length() == "#aarrggbb".length()) {
            color = "#" + color.substring(3);
        }
        mColors.put(yID, color);
    }

    /**
     * Set up data: x, y, and radius values
     * @param xID ID of the x-values array
     * @param yID ID of the y-values array
     * @param s The SeriesData providing the data
     */
    private void setColumns(String xID, String yID, SeriesData s) throws InvalidStateException, JSONException {
        JSONArray xValues = new JSONArray();
        JSONArray yValues = new JSONArray();
        xValues.put(xID);
        yValues.put(yID);

        int barIndex = 0;
        boolean addBarLabels = mData.getType().equals(Graph.TYPE_BAR) && mBarLabels.length() == 1;
        JSONArray rValues = new JSONArray();
        double maxRadius = parseDouble(s.getConfiguration("max-radius", "0"), "max-radius");
        for (XYPointData p : getPoints(s)) {
            String description = "data (" + p.getX() + ", " + p.getY() + ")";
            if (mData.getType().equals(Graph.TYPE_BAR)) {
                // In CommCare, bar graphs are specified with x as a set of text labels
                // and y as a set of values. In C3, bar graphs are still basically
                // of XY graphs, with numeric x and y values. Deal with this by
                // assigning an arbitrary, evenly-spaced x value to each bar and then
                // using the user's x values as custom labels.
                xValues.put(barIndex + 1);
                mBarCount = Math.max(mBarCount, barIndex + 1);
                if (addBarLabels) {
                    mBarLabels.put(p.getX());
                }
            } else {
                xValues.put(parseXValue(p.getX(), description));
            }
            yValues.put(parseYValue(p.getY(), description));

            // Bubble charts also get a radius
            if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
                BubblePointData b = (BubblePointData)p;
                double r = parseRadiusValue(b.getRadius(), description + " with radius " + b.getRadius());
                rValues.put(r);
                maxRadius = Math.max(maxRadius, r);
            }

            barIndex++;
        }
        mColumns.put(xValues);
        mColumns.put(yValues);
        if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            mRadii.put(yID, rValues);
            mMaxRadii.put(yID, maxRadius);
        }
    }

    /**
     * Set series name to display in legend.
     * @param yID ID of y-values array that name applies to
     * @param s SeriesData from which to pull name
     */
    private void setName(String yID, SeriesData s) throws JSONException {
        String name = s.getConfiguration("name", "");
        if (name != null) {
            mNames.put(yID, name);
        }
    }

    private void setPointStyle(String yID, SeriesData s) throws JSONException {
        String symbol;
        if (mData.getType().equals(Graph.TYPE_BAR)) {
            symbol = "none";
        } else if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            symbol = "circle";
        } else {
            symbol = s.getConfiguration("point-style", "circle").toLowerCase();
        }
        mPointStyles.put(yID, symbol);
    }

    /**
     * Set series type: line, bar, area, etc.
     * @param yID ID of y-values array corresponding with series
     * @param s SeriesData determining what the type will be
     */
    private void setType(String yID, SeriesData s) throws JSONException {
        String type = "line";
        if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            type = "scatter";
        } else if (mData.getType().equals(Graph.TYPE_BAR)) {
            type = "bar";
        } else if (s.getConfiguration("fill-below") != null) {
            // TODO: allow customizing fill's color
            type = "area";
        }
        mTypes.put(yID, type);
    }

    /**
     * Set which y axis a series is associated with (primary or secondary).
     * @param yID IS of y-values to associate with the axis
     * @param s SeriesData to pull y axis from
     * @throws JSONException
     */
    private void setYAxis(String yID, SeriesData s) throws JSONException {
        boolean isSecondaryY = Boolean.valueOf(s.getConfiguration("secondary-y", "false"));
        mAxes.put(yID, isSecondaryY ? "y2" : "y");
    }

    /**
     * Comparator to sort XYPointData-derived objects by x value without parsing them.
     * Useful for bar graphs, where x values are text.
     *
     * @author jschweers
     */
    private static class StringPointComparator implements Comparator<XYPointData> {
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
                return Double.valueOf(parseXValue(lhs.getY(), "")).compareTo(parseXValue(rhs.getY(), ""));
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
                return Double.valueOf(parseXValue(rhs.getY(), "")).compareTo(parseXValue(lhs.getY(), ""));
            } catch (InvalidStateException e) {
                return 0;
            }
        }
    }
}
