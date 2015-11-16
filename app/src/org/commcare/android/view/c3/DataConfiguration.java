package org.commcare.android.view.c3;

import org.commcare.android.util.InvalidStateException;
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
    public DataConfiguration(GraphData data) throws JSONException, InvalidStateException {
        super(data);

        // Actual data: array of arrays, where first element is a string id
        // and later elements are data, either x values or y values.
        JSONArray columns = new JSONArray();

        // Hash that pairs up the arrays defined in columns,
        // y-values-array-id => x-values-array-id
        JSONObject xs = new JSONObject();

        // Hash of y-values id => name for legend
        JSONObject names = new JSONObject();

        // Hash of y-values id => 'y' or 'y2' depending on whether this data
        // should be plotted against the primary or secondary y axis
        JSONObject axes = new JSONObject();

        // Hash of y-values id => series color
        JSONObject colors = new JSONObject();

        int seriesIndex = 0;
        for (SeriesData s : mData.getSeries()) {
            JSONArray xValues = new JSONArray();
            JSONArray yValues = new JSONArray();

            String xID = "x" + seriesIndex;
            String yID = "y" + seriesIndex;
            xs.put(yID, xID);

            xValues.put(xID);
            yValues.put(yID);
            for (XYPointData p : s.getPoints()) {
                String description = "point (" + p.getX() + ", " + p.getY() + ")";
                xValues.put(parseXValue(p.getX(), description));
                yValues.put(parseYValue(p.getY(), description));
            }
            columns.put(xValues);
            columns.put(yValues);

            String name = s.getConfiguration("name", "");
            if (name != null) {
                names.put(yID, name);
            }

            String color = s.getConfiguration("line-color", "#ff000000");
            // TODO: Handle transparency
            if (color.length() == "#aarrggbb".length()) {
                color = "#" + color.substring(3);
            }
            colors.put(yID, color);

            axes.put(yID, Boolean.valueOf(s.getConfiguration("secondary-y", "false")).equals(Boolean.TRUE) ? "y2" : "y");

            seriesIndex++;
        }

        mConfiguration.put("axes", axes);
        mConfiguration.put("colors", colors);
        mConfiguration.put("columns", columns);
        mConfiguration.put("names", names);
        mConfiguration.put("xs", xs);
    }
}
