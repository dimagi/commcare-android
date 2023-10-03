package org.commcare.commcaresupportlibrary;

import android.content.Context;
import android.content.Intent;

/**
 * Utility class with methods to launch CommCare
 */
public class CommCareLauncher {
    public static final String SESSION_ENDPOINT_APP_ID = "ccodk_session_endpoint_app_id";
    private static final String CC_LAUNCH_ACTION = "org.commcare.dalvik.action.CommCareSession";

    /**
     *
     * @param context Android context to launch the CommCare with
     * @param appId Unique Id for CommCare App that CommCare should launch with
     */
    public static void launchCommCareForAppId(Context context, String appId) {
        Intent intent = new Intent(CC_LAUNCH_ACTION);
        intent.putExtra(SESSION_ENDPOINT_APP_ID, appId);
        context.startActivity(intent);
    }
}
