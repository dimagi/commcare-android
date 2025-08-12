package org.commcare.commcaresupportlibrary;

import android.content.Context;
import android.content.Intent;

import java.util.HashMap;

/**
 * Utility class with methods to launch CommCare
 */
public class CommCareLauncher {
    public static final String SESSION_ENDPOINT_APP_ID = "ccodk_session_endpoint_app_id";
    private static final String CC_LAUNCH_ACTION = "org.commcare.dalvik.action.CommCareSession";

    public static void launchCommCareForAppId(Context context, String appId) {
       launchCommCareForAppId(context, appId, new HashMap<>());
    }

    public static void launchCommCareForAppId(Context context, String appId, HashMap<String, Object> extras) {
        Intent intent = new Intent(CC_LAUNCH_ACTION);
        intent.putExtra(SESSION_ENDPOINT_APP_ID, appId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP  | Intent.FLAG_ACTIVITY_NEW_TASK);

        extras.forEach((key, value) -> {
            if (value instanceof String) {
                intent.putExtra(key, (String)value);
            } else if (value instanceof Integer) {
                intent.putExtra(key, (Integer)value);
            } else if (value instanceof Boolean) {
                intent.putExtra(key, (Boolean)value);
            } else if (value instanceof Long) {
                intent.putExtra(key, (Long)value);
            } else if (value instanceof Float) {
                intent.putExtra(key, (Float)value);
            } else if (value instanceof Double) {
                intent.putExtra(key, (Double)value);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported type for extra while launching CommCare: " + value.getClass().getName());
            }
        });

        context.startActivity(intent);
    }
}
