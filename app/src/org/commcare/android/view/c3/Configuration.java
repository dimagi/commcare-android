package org.commcare.android.view.c3;

import org.commcare.android.util.InvalidStateException;
import org.commcare.suite.model.graph.GraphData;
import org.javarosa.core.util.OrderedHashtable;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

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
     * Parse given double time value into string acceptable to C3.
     *
     * @param days The time, measured in days since the epoch.
     */
    protected String convertTime(double days) {
        Date d = new Date((long)(days * 24 * 60 * 60 * 1000));
        return mDateFormat.format(d);
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
            return numeric;
        } catch (NumberFormatException nfe) {
            throw new InvalidStateException("Could not understand '" + value + "' in " + description);
        }
    }
}
