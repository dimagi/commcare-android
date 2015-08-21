package org.commcare.android.analytics;

import android.content.SharedPreferences;
import android.util.Log;

import org.commcare.dalvik.application.CommCareApp;

import java.io.IOException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class DownloadStatUtils {
    private static final String TAG = DownloadStatUtils.class.getSimpleName();

    private static final String UPGRADE_STATS = "upgrade_table_stats";

    public static ResourceDownloadStats loadPersistentStats(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        if (prefs.contains(UPGRADE_STATS)) {
            try {
                String serializedObj = prefs.getString(UPGRADE_STATS, "");
                ResourceDownloadStats stats = (ResourceDownloadStats)ResourceDownloadStats.deserialize(serializedObj);
                return stats;
            } catch (Exception e) {
                e.printStackTrace();
                clearPersistedStats(app);
                return new ResourceDownloadStats();
            }
        } else {
            return new ResourceDownloadStats();
        }
    }

    public static void saveStatsPersistently(CommCareApp app, ResourceDownloadStats installStatListener) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        try {
            String serializedObj = ResourceDownloadStats.serialize(installStatListener);
            editor.putString(UPGRADE_STATS, serializedObj);
            editor.commit();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to serialize and store resource installation stats");
        }
    }

    public static void clearPersistedStats(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(UPGRADE_STATS);
        editor.commit();
    }
}
