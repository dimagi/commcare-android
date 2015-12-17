package org.commcare.android.view.c3;

import android.graphics.Color;

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

import java.util.Iterator;

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
    private final JSONObject mXNames = new JSONObject();

    // Hash of y-values id => 'y' or 'y2' depending on whether this data
    // should be plotted against the primary or secondary y axis
    private final JSONObject mAxes = new JSONObject();

    // Hash of y-values id => line, scatter, bar, area, etc.
    private final JSONObject mTypes = new JSONObject();

    // Hash of y-values id => series color
    private final JSONObject mColors = new JSONObject();
    private final JSONObject mLineOpacities = new JSONObject();
    private final JSONObject mAreaColors = new JSONObject();
    private final JSONObject mAreaOpacities = new JSONObject();

    // Array of series that should appear in legend & tooltip
    private final JSONObject mIsData = new JSONObject();

    // Hash of y-values id => point-style string ("circle", "none", "cross", etc.)
    // Doubles as a record of all user-defined series
    // (as opposed to series for annotations, etc.)
    private final JSONObject mPointStyles = new JSONObject();

    // Bar graph data:
    //  mBarCount: for the sake of setting x min and max
    //  mBarLabels: the actual labels to display, which are supposed to be the same
    //      for every series, hence the booleans so we only record them once
    //  mBarColors: hash of y-values id => array of colors, with one color for each bar
    //  mBarOpacities: analagous to mBarColors, but for bar opacitiy values
    private int mBarCount = 0;
    private final JSONArray mBarLabels = new JSONArray("['']");
    private final JSONObject mBarColors = new JSONObject();
    private final JSONObject mBarOpacities = new JSONObject();

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
            setColor(yID, s);
            setName(yID, s);
            setIsData(yID, s);
            setPointStyle(yID, s);
            setType(yID, s);
            setYAxis(yID, s);

            seriesIndex++;
        }

        // Set up separate variables for features that C3 doesn't support well
        mVariables.put("areaColors", mAreaColors.toString());
        mVariables.put("areaOpacities", mAreaOpacities.toString());
        mVariables.put("barColors", mBarColors.toString());
        mVariables.put("barOpacities", mBarOpacities.toString());
        mVariables.put("isData", mIsData.toString());
        mVariables.put("lineOpacities", mLineOpacities.toString());
        mVariables.put("maxRadii", mMaxRadii.toString());
        mVariables.put("pointStyles", mPointStyles.toString());
        mVariables.put("radii", mRadii.toString());
        mVariables.put("xNames", mXNames.toString());

        // Data-based tweaking of user's configuration and adding system series
        normalizeBoundaries();
        addAnnotations();
        addBoundaries();

        // Type-specific logic
        if (mData.getType().equals(Graph.TYPE_TIME)) {
            mConfiguration.put("xFormat", "%Y-%m-%d %H:%M:%S");
        }

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
        JSONObject text = new JSONObject();

        int index = 0;
        for (AnnotationData a : mData.getAnnotations()) {
            String xID = "annotationsX" + index;
            String yID = "annotationsY" + index;
            String description = "annotation '" + a.getAnnotation() + "' at (" + a.getX() + ", " + a.getY() + ")";
            text.put(yID, a.getAnnotation());

            // Add x value
            JSONArray xValues = new JSONArray();
            xValues.put(xID);
            if (mData.getType().equals(Graph.TYPE_TIME)) {
                xValues.put(parseTime(a.getX(), description));
            } else {
                xValues.put(parseDouble(a.getX(), description));
            }
            mColumns.put(xValues);

            // Add y value
            JSONArray yValues = new JSONArray();
            yValues.put(yID);
            yValues.put(parseDouble(a.getY(), description));
            mColumns.put(yValues);

            // Configure series
            mXs.put(yID, xID);
            mTypes.put(yID, "line");
            mAxes.put(yID, "y");

            index++;
        }

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
            if (mData.getType().equals(Graph.TYPE_TIME)) {
                xValues.put(parseTime(xMin, "x-min"));
                xValues.put(parseTime(xMax, "x-max"));
            } else {
                xValues.put(parseDouble(xMin, "x-min"));
                xValues.put(parseDouble(xMax, "x-max"));
            }
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
            mAxes.put(yID, prefix.startsWith("secondary") ? "y2" : "y");

            JSONArray yValues = new JSONArray();
            yValues.put(yID);
            yValues.put(parseDouble(min, prefix + "-min"));
            yValues.put(parseDouble(max, prefix + "-max"));
            mColumns.put(yValues);
            return true;
        }
        return false;
    }

    /**
     * Set up stacked bar graph, if needed. Expects series data to have
     * already been processed (specifically, expects mTypes to be populated).
     * @return JSONArray of configuration for groups, C3's version of stacking
     */
    private JSONArray getGroups() throws JSONException {
        JSONArray outer = new JSONArray();
        JSONArray inner = new JSONArray();
        if (mData.getType().equals(Graph.TYPE_BAR)
                && Boolean.valueOf(mData.getConfiguration("stack", "false"))) {
            for (Iterator<String> i = mTypes.keys(); i.hasNext();) {
                String key = i.next();
                if (mTypes.get(key).equals("bar")) {
                    inner.put(key);
                }
            }
        } else {
            for (Iterator<String> i = mTypes.keys(); i.hasNext();) {
                String yID = i.next();
                if (mTypes.getString(yID).equals("area")) {
                    inner.put(yID);
                }
            }
        }

        if (inner.length() > 0) {
            outer.put(inner);
        }
        return outer;
    }

    /**
     * For bar charts, set up bar labels and force the x axis min and max so bars are spaced nicely
     */
    private void normalizeBoundaries() throws JSONException {
        if (mData.getType().equals(Graph.TYPE_BAR)) {
            mData.setConfiguration("x-min", "0.5");
            mData.setConfiguration("x-max", String.valueOf(mBarCount + 0.5));
            mBarLabels.put("");
            mVariables.put("barLabels", mBarLabels.toString());

            // Force all labels to show; C3 will hide some labels if it thinks there are too many.
            JSONObject xLabels = new JSONObject();
            for (int i = 0; i < mBarLabels.length(); i++) {
                xLabels.put(String.valueOf(i), (String) mBarLabels.get(i));
            }
            mData.setConfiguration("x-labels", xLabels.toString());
        }
    }

    /**
     * Set color for a given series.
     * @param yID ID of y-values array to set color
     * @param s SeriesData from which to pull color
     */
    private void setColor(String yID, SeriesData s) throws JSONException {
        String barColorJSON = s.getConfiguration("bar-color");
        if (barColorJSON != null) {
            JSONArray requestedColors = new JSONArray(barColorJSON);
            JSONArray colors = new JSONArray();
            JSONArray opacities = new JSONArray();
            for (int i = 0; i < requestedColors.length(); i++) {
                String color = requestedColors.getString(i);
                color = normalizeColor(color);
                colors.put(i, "#" + color.substring(3));
                opacities.put(getOpacity(color));
            }
            mBarColors.put(yID, colors);
            mBarOpacities.put(yID, opacities);
            return;
        }

        String color = s.getConfiguration("line-color", "#ff000000");
        color = normalizeColor(color);
        mColors.put(yID, "#" + color.substring(3));
        mLineOpacities.put(yID, getOpacity(color));

        String fillBelow = s.getConfiguration("fill-below");
        if (fillBelow != null) {
            fillBelow = normalizeColor(fillBelow);
            mAreaColors.put(yID, "#" + fillBelow.substring(3));
            mAreaOpacities.put(yID, getOpacity(fillBelow));
        }
    }

    /**
     * Convert color string to expected format.
     * @param color String of format #?(AA)?RRGGBB
     * @return String of format "#AARRGGBB"
     */
    private String normalizeColor(String color) {
        if (color.length() % 2 == 0) {
            color = "#" + color;
        }
        if (color.length() == 7) {
            color = "#ff" + color.substring(1);
        }
        return color;
    }

    /**
     * Calculate opacity of given color.
     * @param color Color in format "#AARRGGBB"
     * @return Opacity, which will be between 0 and 1, inclusive
     */
    private double getOpacity(String color) {
        return Color.alpha(Color.parseColor(color)) / (double) 255;
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
        for (XYPointData p : s.getPoints()) {
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
                if (mData.getType().equals(Graph.TYPE_TIME)) {
                    xValues.put(parseTime(p.getX(), description));
                } else {
                    xValues.put(parseDouble(p.getX(), description));
                }
            }
            yValues.put(parseDouble(p.getY(), description));

            // Bubble charts also get a radius
            if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
                BubblePointData b = (BubblePointData)p;
                double r = parseDouble(b.getRadius(), description + " with radius " + b.getRadius());
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
     * Set whether or not point should appear in legend and tooltip.
     * @param yID ID of y-values array that is or isn't data
     * @param s SeriesData from which to pull flag
     */
    private void setIsData(String yID, SeriesData s) throws JSONException {
        boolean isData = Boolean.valueOf(s.getConfiguration("is-data", "true"));
        if (isData) {
            mIsData.put(yID, 1);
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
        mXNames.put(yID, s.getConfiguration("x-name", mData.getConfiguration("x-title", "x")));
    }

    /**
     * Set shape of points to be drawn for series.
     * @param yID ID of y-values that style applies to
     * @param s SeriesData from which to pull style
     */
    private void setPointStyle(String yID, SeriesData s) throws JSONException {
        String symbol;
        if (mData.getType().equals(Graph.TYPE_BAR) || mData.getType().equals(Graph.TYPE_BUBBLE)) {
            // point-style doesn't apply to bar charts
            symbol = "none";
        } else if (mData.getType().equals(Graph.TYPE_BUBBLE)) {
            // point-style doesn't apply to bubble charts,
            // but this'll make the legend symbol a circle
            symbol = "circle";
        } else {
            symbol = s.getConfiguration("point-style", "circle").toLowerCase();
        }
        if (symbol.equals("triangle")) {
            symbol = "triangle-up";
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
}
