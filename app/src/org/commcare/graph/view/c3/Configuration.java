package org.commcare.graph.view.c3;

import org.commcare.graph.model.GraphData;
import org.commcare.graph.util.GraphException;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Base class for helper classes that build C3 graph configuration.
 * This class itself is not meant to be instantiated. For subclasses,
 * the bulk of the work is done in the constructor. The instantiator
 * can then call getConfiguration and getVariables to get at the JSON
 * configuration and any JavaScript variables that configuration depends on.
 *
 * Created by jschweers on 11/16/2015.
 */
public class Configuration {
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    final GraphData mData;
    final JSONObject mConfiguration;
    final SortedMap<String, String> mVariables;

    Configuration(GraphData data) {
        mData = data;
        mConfiguration = new JSONObject();
        mVariables = new TreeMap<>();
    }

    public JSONObject getConfiguration() {
        return mConfiguration;
    }

    public SortedMap<String, String> getVariables() {
        return mVariables;
    }

    /**
     * Parse given time value into string acceptable to C3.
     *
     * @param value       The value, which may be a YYYY-MM-DD string, a YYYY-MM-DD HH:MM:SS,
     *                    or a double representing days since the epoch.
     * @param description Something to identify the kind of value, used to augment any error message.
     * @return String of format YYYY-MM-DD HH:MM:SS, which is what C3 expects.
     * This expected format is set in DataConfiguration as xFormat.
     * @throws GraphException
     */
    String parseTime(String value, String description) throws GraphException {
        if (value.matches(".*[^0-9.].*")) {
            if (!value.matches(".*:.*")) {
                value += " 00:00:00";
            }
        } else {
            double daysSinceEpoch = parseDouble(value, description);
            Date d = new Date((long)(daysSinceEpoch * 86400000l));
            value = mDateFormat.format(d);
        }
        return value;
    }

    /**
     * Attempt to parse a double, but fail on NumberFormatException.
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    double parseDouble(String value, String description) throws GraphException {
        try {
            Double numeric = Double.valueOf(value);
            if (numeric.isNaN()) {
                throw new GraphException("Could not understand '" + value + "' in " + description);
            }
            return numeric;
        } catch (NumberFormatException nfe) {
            throw new GraphException("Could not understand '" + value + "' in " + description);
        }
    }
}
