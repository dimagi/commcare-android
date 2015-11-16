package org.commcare.android.view.c3;

import org.commcare.android.util.InvalidStateException;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.GraphData;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.util.OrderedHashtable;
import org.json.JSONObject;

import java.util.Date;

/**
 * Created by jschweers on 11/16/2015.
 */
public class Configuration {
    protected GraphData mData;
    protected JSONObject mConfiguration;
    protected OrderedHashtable<String, String> mVariables;

    public Configuration(GraphData data) {
        mData = data;
        mConfiguration = new JSONObject();
        mVariables = new OrderedHashtable<>();
    }

    public JSONObject getConfiguration() {
        return mConfiguration;
    }

    public OrderedHashtable<String, String> getVariables() {
        return mVariables;
    }


    /**
     * Parse given string into double
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    protected double parseXValue(String value, String description) throws InvalidStateException {
        if (Graph.TYPE_TIME.equals(mData.getType())) {
            Date parsed = DateUtils.parseDateTime(value);
            if (parsed == null) {
                throw new InvalidStateException("Could not parse date '" + value + "' in " + description);
            }
            return parseDouble(String.valueOf(parsed.getTime()), description);
        }

        return parseDouble(value, description);
    }

    /**
     * Parse given string into double
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    protected double parseYValue(String value, String description) throws InvalidStateException {
        return parseDouble(value, description);
    }

    /**
     * Parse given string into double
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    protected double parseRadiusValue(String value, String description) throws InvalidStateException {
        return parseDouble(value, description);
    }

    /**
     * Attempt to parse a double, but fail on NumberFormatException.
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    protected double parseDouble(String value, String description) throws InvalidStateException {
        try {
            Double numeric = Double.valueOf(value);
            if (numeric.isNaN()) {
                throw new InvalidStateException("Could not understand '" + value + "' in " + description);
            }
            return numeric.doubleValue();
        } catch (NumberFormatException nfe) {
            throw new InvalidStateException("Could not understand '" + value + "' in " + description);
        }
    }
}
