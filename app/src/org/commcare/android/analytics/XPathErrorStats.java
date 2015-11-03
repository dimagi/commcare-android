package org.commcare.android.analytics;

import org.commcare.dalvik.application.CommCareApp;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class XPathErrorStats implements Serializable {
    private static final String XPATH_ERROR_STATS_KEY = "xpath_error_stats";
    private final ArrayList<XPathErrorEntry> errors;

    private XPathErrorStats() {
        errors = new ArrayList<>();
    }

    /**
     * Load update statistics associated with upgrade table from app
     * preferences
     *
     * @return Persistently-stored update stats or if no stats found then a new
     * update stats object.
     */
    public static XPathErrorStats loadStats(CommCareApp app) {
        Object stats = PrefStats.loadStats(app, XPATH_ERROR_STATS_KEY);
        if (stats != null) {
            return (XPathErrorStats)stats;
        } else {
            return new XPathErrorStats();
        }
    }

    /**
     * Save update stats to app preferences for reuse if the update is ever
     * resumed.
     */
    public static void saveStats(CommCareApp app,
                                 XPathErrorStats stats) {
        PrefStats.saveStatsPersistently(app, XPATH_ERROR_STATS_KEY, stats);
    }

    /**
     * Wipe stats associated with upgrade table from app preferences.
     */
    public static void clearStats(CommCareApp app) {
        PrefStats.clearPersistedStats(app, XPATH_ERROR_STATS_KEY);
    }

    public void addError(String expression, String errorMessage) {
        XPathErrorEntry error = new XPathErrorEntry(expression, errorMessage);
        errors.add(error);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (XPathErrorEntry error : errors) {
            stringBuilder.append(error.toString()).append("\n");
        }
        return stringBuilder.toString();
    }
}
