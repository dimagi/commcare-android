package org.commcare.sync;

import static org.commcare.utils.Permissions.COMMCARE_CASE_READ_PERMISSION;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.javarosa.core.services.Logger;

import java.util.ArrayList;

import javax.annotation.Nullable;

public class ExternalDataUpdateHelper {

    public static final String COMMCARE_DATA_UPDATE_ACTION = "org.commcare.dalvik.api.action.data.update";

    /**
     * Broadcasts data update to external apps
     * @param c context to send the broadcast with
     * @param updatedCases list of cases updated or created in the update
     */
    public static void broadcastDataUpdate(Context c,
                                           @Nullable ArrayList<String> updatedCases) {
        Intent i = new Intent(COMMCARE_DATA_UPDATE_ACTION);
        if (updatedCases != null) {
            i.putStringArrayListExtra("cases", updatedCases);
        }

        // This is to be used by extenal apps to run any user based filtering
        if (CommCareApplication.instance().getSession().isActive()) {
            i.putExtra("cc-logged-in-user-id", CommCareApplication.instance().getCurrentUserId());
        }
        sendBroadcastFailSafe(c, i, COMMCARE_CASE_READ_PERMISSION);

        // send explicit broadcast to CommCare Reminders App
        i.setComponent(new ComponentName("org.commcare.dalvik.reminders",
                "org.commcare.dalvik.reminders.CommCareReceiver"));
        sendBroadcastFailSafe(c, i, null);

        // Broadcast to CommCare, there is the option to handle the permission required by the
        // broadcast above
        broadcastDataUpdateToCommCare(c);
    }

    // send explicit broadcast to CommCare
    private static void broadcastDataUpdateToCommCare(Context c){
        Intent i = new Intent(COMMCARE_DATA_UPDATE_ACTION);
        i.setPackage(c.getPackageName());
        sendBroadcastFailSafe(c, i, null);
    }

    public static void sendBroadcastFailSafe(Context context, Intent intent, @Nullable String receiverPermission) {
        try {
            context.sendBroadcast(intent, receiverPermission);
        } catch (Exception e) {
            Logger.exception("Exception when sending a broadcast with intent " + intent, e);
        }
    }
}