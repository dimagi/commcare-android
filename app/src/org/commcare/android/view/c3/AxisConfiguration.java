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

        JSONObject x = getAxis("x");
        JSONObject y = getAxis("y");
        JSONObject y2 = getAxis("secondary-y");

        if (mData.getType().equals(Graph.TYPE_TIME)) {
            x.put("type", "timeseries");
        }

        // Display secondary y axis only if it has at least one associated series
        for (SeriesData s : mData.getSeries()) {
            boolean hasSecondaryAxis = Boolean.valueOf(s.getConfiguration("secondary-y", "false"));
            if (hasSecondaryAxis) {
                y2.put("show", true);
                break;
            }
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
     */
    private void addBound(JSONObject axis, String prefix, String suffix) throws JSONException, InvalidStateException {
        String key = prefix + "-" + suffix;
        String value = mData.getConfiguration(key);
        if (value != null) {
            if (prefix.equals("x") && mData.getType().equals(Graph.TYPE_TIME)) {
                axis.put(suffix, value);
            } else {
                axis.put(suffix, parseDouble(value, key));
            }

        }
    }

    /**
     * Configure tick count, placement, and labels.
     * @param axis Current axis configuration. Will be modified.
     * @param key One of "x-labels", "y-labels", "secondary-y-labels"
     * @param varName If the axis uses a hash of labels (position => label), a variable
     *                will be created with this name to store those labels.
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
                    String xValue = labels.getString(i);
                    if (mData.getType().equals(Graph.TYPE_TIME)) {
                        values.put(xValue);
                    } else {
                        values.put(parseDouble(xValue, key));
                    }
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
                        if (mData.getType().equals(Graph.TYPE_TIME)) {
                            values.put(location);
                        } else {
                            values.put(parseDouble(location, key));
                        }
                    }
                    tick.put("values", values);
                    mVariables.put(varName, labels.toString());
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

    /**
     * Generate axis configuration.
     * @param prefix Prefix for commcare model's configuration: "x", "y", or "secondary-y"
     * @return JSONObject representing the axis's configuration
     */
    private JSONObject getAxis(String prefix) throws JSONException, InvalidStateException {
        final boolean showAxes = Boolean.valueOf(mData.getConfiguration("show-axes", "true"));
        if (!showAxes) {
            return new JSONObject("{ show: false }");
        }

        JSONObject config = new JSONObject();
        boolean isX = prefix.equals("x");

        // Undo C3's automatic axis padding
        config.put("padding", new JSONObject("{top: 0, right: 0, bottom: 0, left: 0}"));

        addTitle(config, prefix + "-title", isX ? "outer-center" : "outer-middle");

        addBounds(config, prefix);

        String jsPrefix = prefix.equals("secondary-y") ? "y2" : prefix;
        addTickConfig(config, prefix + "-labels", jsPrefix + "Labels");

        return config;
    }
}
