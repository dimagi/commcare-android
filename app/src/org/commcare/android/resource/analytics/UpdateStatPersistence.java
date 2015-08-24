package org.commcare.android.resource.analytics;

import android.content.SharedPreferences;
import android.util.Log;

import org.commcare.dalvik.application.CommCareApp;

import java.io.IOException;

/**
 * Saves and loads stats associated with an app update job to shared preferences.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateStatPersistence {
    private static final String TAG = UpdateStatPersistence.class.getSimpleName();

    private static final String UPGRADE_STATS_KEY = "upgrade_table_stats";

    /**
     * Load update statistics associated with upgrade table from app preferences
     *
     * @return Persistently-stored update stats or if no stats found then a new
     * update stats object.
     */
    public static UpdateStats loadUpdateStats(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        if (prefs.contains(UPGRADE_STATS_KEY)) {
            try {
                String serializedObj = prefs.getString(UPGRADE_STATS_KEY, "");
                return (UpdateStats)UpdateStats.deserialize(serializedObj);
            } catch (Exception e) {
                Log.w(TAG, "Failed to deserialize update stats, defaulting to new instance.");
                e.printStackTrace();
                clearPersistedStats(app);
                return new UpdateStats();
            }
        } else {
            return new UpdateStats();
        }
    }

    /**
     * Save update stats to app preferences for reuse if the update is ever resumed.
     */
    public static void saveStatsPersistently(CommCareApp app,
                                             UpdateStats updateStats) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        try {
            String serializedObj = UpdateStats.serialize(updateStats);
            editor.putString(UPGRADE_STATS_KEY, serializedObj);
            editor.commit();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to serialize and store resource installation stats");
        }
    }

    /**
     * Wipe stats associated with upgrade table from app preferences.
     */
    public static void clearPersistedStats(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(UPGRADE_STATS_KEY);
        editor.commit();
    }
}
