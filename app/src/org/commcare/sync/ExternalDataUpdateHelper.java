package org.commcare.sync;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;

import javax.annotation.Nullable;

public class ExternalDataUpdateHelper {

    /**
     * Broadcasts data update to external apps
     * @param c context to send the broadcast with
     * @param updatedCases list of cases updated or created in the update
     */
    public static void broadcastDataUpdate(Context c, @Nullable ArrayList<String> updatedCases) {
        Intent i = new Intent("org.commcare.dalvik.api.action.data.update");
        if (updatedCases != null) {
            i.putStringArrayListExtra("cases", updatedCases);
        }
        c.sendBroadcast(i, "org.commcare.dalvik.provider.cases.read");

        // send explicit broadcast to CommCare Reminders App
        i.setComponent(new ComponentName("org.commcare.dalvik.reminders",
                "org.commcare.dalvik.reminders.CommCareReceiver"));
        c.sendBroadcast(i);
    }
}
