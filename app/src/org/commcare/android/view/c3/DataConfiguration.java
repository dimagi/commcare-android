package org.commcare.android.view.c3;

import org.commcare.android.util.InvalidStateException;
import org.commcare.suite.model.graph.AnnotationData;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.GraphData;
import org.commcare.suite.model.graph.SeriesData;
import org.commcare.suite.model.graph.XYPointData;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

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

        int seriesIndex = 0;
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
            if (s.getConfiguration("fill-below") != null) {
                type = "area";
            }
            mTypes.put(yID, type);

            xValues.put(xID);
            yValues.put(yID);
            for (XYPointData p : s.getPoints()) {
                String description = "point (" + p.getX() + ", " + p.getY() + ")";
                xValues.put(parseXValue(p.getX(), description));
                yValues.put(parseYValue(p.getY(), description));
            }
            mColumns.put(xValues);
            mColumns.put(yValues);

            String name = s.getConfiguration("name", "");
            if (name != null) {
                mNames.put(yID, name);
            }

            String color = s.getConfiguration("line-color", "#ff000000");
            // TODO: Handle transparency
            if (color.length() == "#aarrggbb".length()) {
                color = "#" + color.substring(3);
            }
            mColors.put(yID, color);

            mAxes.put(yID, Boolean.valueOf(s.getConfiguration("secondary-y", "false")).equals(Boolean.TRUE) ? "y2" : "y");

            seriesIndex++;
        }

        addBoundaries();

        addAnnotations();

        mConfiguration.put("axes", mAxes);
        mConfiguration.put("colors", mColors);
        mConfiguration.put("columns", mColumns);
        mConfiguration.put("names", mNames);
        mConfiguration.put("types", mTypes);
        mConfiguration.put("xs", mXs);
    }

    private void addAnnotations() throws JSONException, InvalidStateException {
        String xID = "annotationsX";
        String yID = "annotationsY";

        mXs.put(yID, xID);
        mTypes.put(yID, "line");

        JSONArray xValues = new JSONArray();
        xValues.put(xID);
        JSONArray yValues = new JSONArray();
        yValues.put(yID);
        JSONArray text = new JSONArray();

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
        String yMin = mData.getConfiguration("y-min");
        String yMax = mData.getConfiguration("y-max");

        if (xMin == null || xMax == null || yMin == null || yMax == null) {
            return;
        }

        String xID = "boundsX";
        String yID = "boundsY";

        mXs.put(yID, xID);
        mTypes.put(yID, "line");

        JSONArray xValues = new JSONArray();
        xValues.put(xID);
        xValues.put(parseXValue(xMin, "x-min"));
        xValues.put(parseXValue(xMax, "x-max"));

        JSONArray yValues = new JSONArray();
        yValues.put(yID);
        yValues.put(parseYValue(yMin, "y-min"));
        yValues.put(parseYValue(yMax, "y-max"));

        mColumns.put(xValues);
        mColumns.put(yValues);
    }
}
