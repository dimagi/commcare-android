package org.commcare.commcaresupportlibrary;

import android.content.Context;
import android.content.Intent;

/**
 * Utility class with methods to launch CommCare
 */
public class CommCareLauncher {
    public static final String SESSION_ENDPOINT_APP_ID = "ccodk_session_endpoint_app_id";
    public static final String EXTRA_FROM_CONNECT = "extra_from_connect";
    private static final String CC_LAUNCH_ACTION = "org.commcare.dalvik.action.CommCareSession";

    /**
     *
     * @param context Android context to launch the CommCare with
     * @param appId Unique Id for CommCare App that CommCare should launch with
     */
    public static void launchCommCareForAppId(Context context, String appId) {
        launchCommCareForAppId(context, appId, false);
    }

    /**
     *
     * @param context Android context to launch the CommCare with
     * @param appId Unique Id for CommCare App that CommCare should launch with
     */
    public static void launchCommCareForAppIdFromConnect(Context context, String appId) {
        launchCommCareForAppId(context, appId, true);
    }

    private static void launchCommCareForAppId(Context context, String appId, boolean fromConnect) {
        Intent intent = new Intent(CC_LAUNCH_ACTION);
        intent.putExtra(SESSION_ENDPOINT_APP_ID, appId);
        intent.putExtra(EXTRA_FROM_CONNECT, fromConnect);
        context.startActivity(intent);
    }
}
