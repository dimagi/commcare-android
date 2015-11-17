package org.commcare.android.view.c3;

import org.commcare.android.util.InvalidStateException;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.GraphData;
import org.commcare.suite.model.graph.SeriesData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Axis-related configuration for C3.
 *
 * Created by jschweers on 11/16/2015.
 */
public class AxisConfiguration extends Configuration {
    public AxisConfiguration(GraphData data) throws JSONException, InvalidStateException {
        super(data);

        JSONObject x = new JSONObject();
        JSONObject y = new JSONObject();
        JSONObject y2 = new JSONObject();

        if (Boolean.valueOf(mData.getConfiguration("show-axes", "true")).equals(Boolean.FALSE)) {
            JSONObject show = new JSONObject("{ show: false }");
            x = show;
            y = show;
            y2 = show;
        } else {
            // Undo C3's automatic axis padding
            JSONObject padding = new JSONObject("{top: 0, right: 0, bottom: 0, left: 0}");
            x.put("padding", padding);
            y.put("padding", padding);
            y2.put("padding", padding);

            // Axis titles
            addTitle(x, "x-title", "outer-center");
            addTitle(y, "y-title", "outer-middle");
            addTitle(y2, "secondary-y-title", "outer-middle");

            // Min and max boundaries
            // TODO: verify x-min and x-max work with time-based graphs
            addBounds(x, "x");
            addBounds(y, "y");
            addBounds(y2, "secondary-y");

            // Display secondary y axis only if it has at least one associated series
            for (SeriesData s : mData.getSeries()) {
                if (Boolean.valueOf(s.getConfiguration("secondary-y", "false")).equals(Boolean.TRUE)) {
                    y2.put("show", true);
                    break;
                }
            }

            // Axis tick labels
            addTickConfig(x, "x-labels", "xLabels");
            addTickConfig(y, "y-labels", "yLabels");
            addTickConfig(y2, "secondary-y-labels", "y2Labels");
        }

        mConfiguration.put("x", x);
        mConfiguration.put("y", y);
        mConfiguration.put("y2", y2);

        // Bar graphs may be rotated. C3 defaults to vertical bars.
        if (mData.getType().equals(Graph.TYPE_BAR)
                && !mData.getConfiguration("bar-orientation", "horizontal").equalsIgnoreCase("vertical")) {
            mConfiguration.put("rotated", true);
        }
    }

    /**
     * Add min and max bounds to given axis.
     * @param axis Current axis configuration. Will be modified.
     * @param prefix Prefix for commcare model's configuration: "x", "y", or "secondary-y"
     * @throws InvalidStateException
     * @throws JSONException
     */
    private void addBounds(JSONObject axis, String prefix) throws InvalidStateException, JSONException {
        addBound(axis, prefix, "min");
        addBound(axis, prefix, "max");
    }

    /**
     * Add min or max bound to given axis.
     * @param axis Current axis configuratoin. Will be modified.
     * @param prefix Prefix for commcare model's configuration: "x", "y", or "secondary-y"
     * @param suffix "min" or "max"
     * @throws JSONException
     * @throws InvalidStateException
     */
    private void addBound(JSONObject axis, String prefix, String suffix) throws JSONException, InvalidStateException {
        String key = prefix + "-" + suffix;
        String value = mData.getConfiguration(key);
        if (value != null) {
            double parsed = prefix.equals("x") ? parseXValue(value, key) : parseYValue(value, key);
            axis.put(suffix, parsed);
        }
    }

    /**
     * Configure tick count, placement, and labels.
     * @param axis Current axis configuration. Will be modified.
     * @param key One of "x-labels", "y-labels", "secondary-y-labels"
     * @param varName If the axis uses a hash of labels (position => label), a variable
     *                will be created with this name to store those labels.
     * @throws InvalidStateException
     * @throws JSONException
     */
    private void addTickConfig(JSONObject axis, String key, String varName) throws InvalidStateException, JSONException {
        // The labels configuration might be a JSON array of numbers,
        // a JSON object of number => string, or a single number
        String labelString = mData.getConfiguration(key);
        JSONObject tick = new JSONObject();

        mVariables.put(varName, "{}");
        if (labelString != null) {
            try {
                // Array: label each given value
                JSONArray labels = new JSONArray(labelString);
                JSONArray values = new JSONArray();
                for (int i = 0; i < labels.length(); i++) {
                    values.put(parseXValue(labels.getString(i), key));   // TODO: verify this works for time graphs
                }
                tick.put("values", values);
            } catch (JSONException je) {
                // Assume try block failed because labelString isn't an array.
                // Try parsing it as an object.
                try {
                    // Object: each key is a location on the axis,
                    // and the value is text with which to label it
                    JSONObject labels = new JSONObject(labelString);
                    JSONArray values = new JSONArray();
                    Iterator i = labels.keys();
                    while (i.hasNext()) {
                        String location = (String)i.next();
                        values.put(parseXValue(location, key));
                    }
                    tick.put("values", values);
                    mVariables.put(varName, labels.toString()); // TODO: verify this works for time graphs
                } catch (JSONException e) {
                    // Assume labelString is just a scalar, which
                    // represents the number of labels the user wants.
                    tick.put("count", Integer.valueOf(labelString));
                }
            }
        }

        axis.put("tick", tick);
    }

    /**
     * Add title to axis.
     * @param axis Current axis configuration. Will be modified.
     * @param key One of "x-title", "y-title", "secondary-y-title"
     * @param position For horizontal axis, (inner|outer)-(right|center|left)
     *                 For vertical axis, (inner|outer)-(top|middle|bottom)
     * @throws JSONException
     */
    private void addTitle(JSONObject axis, String key, String position) throws JSONException {
        String title = mData.getConfiguration(key, "");

        // String.trim doesn't cover characters like unicode's non-breaking space
        title = title.replaceAll("^\\s*", "");
        title = title.replaceAll("\\s*$", "");

        if (!"".equals(title)) {
            JSONObject label = new JSONObject();
            label.put("text", title);
            label.put("position", position);
            axis.put("label", label);
        }
    }

}
