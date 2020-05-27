package org.commcare.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import java.util.UUID;

/**
 * A Utility to get a unique identifier for app's installation.
 * @author $|-|!˅@M
 */
public final class DeviceIdentifier {

    private static volatile String uuid;
    private static final String PREFIX = "commcare_";
    private static final String KEY_DEVICE_IDENTIFIER = "commcare_device_id";

    /**
     * Returns a unique(very highly likely) identifier for this device.
     * This identifier is prefixed with "commcare_"
     *
     * This id may change when user re-installs the app or does a factory reset.
     */
    public static String getDeviceIdentifier(Context context) {
        if (uuid == null) {
            synchronized (DeviceIdentifier.class) {
                if (uuid == null) {
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                    String id = sharedPreferences.getString(KEY_DEVICE_IDENTIFIER, null);
                    if (id != null) {
                        uuid = id;
                    } else {
                        uuid = PREFIX + UUID.randomUUID().toString();
                        sharedPreferences.edit().putString(KEY_DEVICE_IDENTIFIER, uuid).apply();
                    }
                }
            }
        }
        return uuid;
    }
}