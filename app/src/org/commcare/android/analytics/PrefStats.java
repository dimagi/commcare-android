package org.commcare.android.analytics;

import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.commcare.dalvik.application.CommCareApp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class PrefStats {
    private static final String TAG = PrefStats.class.getSimpleName();

    private static Object deserialize(String s) throws IOException,
            ClassNotFoundException {
        byte[] data = Base64.decode(s, Base64.DEFAULT);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    /**
     * Save stats to app preferences.
     */
    protected static void saveStatsPersistently(CommCareApp app,
                                             String key,
                                             Serializable stats) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        try {
            String serializedObj = serialize(stats);
            editor.putString(key, serializedObj);
            editor.commit();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to serialize and store stats");
        }
    }

    private static String serialize(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    /**
     * Wipe stats from app preferences.
     */
    protected static void clearPersistedStats(CommCareApp app, String key) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(key);
        editor.commit();
    }

    /**
     * Load statistics from app preferences
     *
     * @return Persistently-stored stats or null if no stats are found
     */
    protected static Object loadStats(CommCareApp app, String key) {
        SharedPreferences prefs = app.getAppPreferences();
        if (prefs.contains(key)) {
            try {
                String serializedObj = prefs.getString(key, "");
                return deserialize(serializedObj);
            } catch (Exception e) {
                Log.w(TAG, "Failed to deserialize stats.");
                e.printStackTrace();
                clearPersistedStats(app, key);
            }
        }
        return null;
    }

}
