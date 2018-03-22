package org.commcare.heartbeat;

import android.support.v7.app.AppCompatActivity;
import android.content.Intent;
import android.util.Base64;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.PromptApkUpdateActivity;
import org.commcare.activities.PromptCczUpdateActivity;
import org.commcare.services.CommCareSessionService;
import org.commcare.util.LogTypes;
import org.commcare.utils.SerializationUtil;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.services.Logger;

/**
 * Created by amstone326 on 5/5/17.
 */

public class UpdatePromptHelper {

    /**
     * Check if we have an UpdateToPrompt to show, and launch the necessary activity if so.
     * We check for an apk update first, which means that if there are updates to prompt for both,
     * we'll show the apk one first.
     *
     * @return - If the user was prompted to update
     */
    public static boolean promptForUpdateIfNeeded(AppCompatActivity context) {
        try {
            CommCareSessionService currentSession = CommCareApplication.instance().getSession();
            if (!currentSession.apkUpdatePromptWasShown()) {
                UpdateToPrompt apkUpdate = getCurrentUpdateToPrompt(UpdateToPrompt.Type.APK_UPDATE);
                if (apkUpdate != null && apkUpdate.shouldShowOnThisLogin()) {
                    Intent i = new Intent(context, PromptApkUpdateActivity.class);
                    context.startActivity(i);
                    return true;
                }
            }
            if (!currentSession.cczUpdatePromptWasShown()) {
                UpdateToPrompt cczUpdate = getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE);
                if (cczUpdate != null && cczUpdate.shouldShowOnThisLogin()) {
                    Intent i = new Intent(context, PromptCczUpdateActivity.class);
                    context.startActivity(i);
                    return true;
                }
            }
        } catch (SessionUnavailableException e) {
            // Means we just performed an update and have therefore expired the session
        }
        return false;
    }

    /**
     * @return an UpdateToPrompt that has been stored in SharedPreferences and is still relevant
     * (i.e. the user hasn't updated to or past this version since we stored it)
     */
    public static UpdateToPrompt getCurrentUpdateToPrompt(UpdateToPrompt.Type type) {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (currentApp != null) {
            String serializedUpdate = currentApp.getAppPreferences().getString(type.getPrefsKey(), "");
            if (!"".equals(serializedUpdate)) {
                byte[] updateBytes = Base64.decode(serializedUpdate, Base64.DEFAULT);
                UpdateToPrompt update;
                try {
                     update = SerializationUtil.deserialize(updateBytes, UpdateToPrompt.class);
                } catch (Exception e) {
                    // Something went wrong, so clear out whatever is there
                    Logger.log(LogTypes.TYPE_ERROR_WORKFLOW,
                            "Error encountered while de-serializing saved UpdateToPrompt: "
                                    + e.getMessage());
                    wipeStoredUpdate(type);
                    return null;
                }
                if (update.isNewerThanCurrentVersion()) {
                    return update;
                } else {
                    // The update we had stored is no longer relevant, so wipe it and return nothing
                    wipeStoredUpdate(type);
                    return null;
                }
            }
        }
        return null;
    }

    protected static void wipeStoredUpdate(UpdateToPrompt.Type type) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putString(type.getPrefsKey(), "").apply();
    }

}
