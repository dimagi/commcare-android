package org.commcare.sync;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;

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
        // This is to be used to skip any cases that are not owned by the user but that might be
        // in the database at the time of the data refresh
        if (CommCareApplication.instance().getSession().isActive()) {
            i.putExtra("logged-in-user-id", CommCareApplication.instance().getCurrentUserId());
        }
        c.sendBroadcast(i, "org.commcare.dalvik.provider.cases.read");

        // send explicit broadcast to CommCare Reminders App
        i.setComponent(new ComponentName("org.commcare.dalvik.reminders",
                "org.commcare.dalvik.reminders.CommCareReceiver"));
        c.sendBroadcast(i);
    }
}
