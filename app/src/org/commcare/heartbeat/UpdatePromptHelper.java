package org.commcare.heartbeat;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.PromptApkUpdateActivity;
import org.commcare.activities.PromptCczUpdateActivity;
import org.commcare.activities.PromptUpdateActivity;
import org.commcare.logging.AndroidLogger;
import org.commcare.services.CommCareSessionService;
import org.commcare.utils.SerializationUtil;
import org.javarosa.core.services.Logger;

/**
 * Created by amstone326 on 5/5/17.
 */

public class UpdatePromptHelper {

    /**
     * @return - If the user was prompted to update
     */
    public static boolean promptForUpdateIfNeeded(Activity context, int requestCode) {
        CommCareSessionService currentSession = CommCareApplication.instance().getSession();
        if (!currentSession.apkUpdatePromptWasShown() &&
                getCurrentUpdateToPrompt(UpdateToPrompt.Type.APK_UPDATE) != null) {
            // If there are updates to prompt for both, we'll show the apk one first
            Intent i = new Intent(context, PromptApkUpdateActivity.class);
            context.startActivityForResult(i, requestCode);
            return true;
        } else if (!currentSession.cczUpdatePromptWasShown() &&
                getCurrentUpdateToPrompt(UpdateToPrompt.Type.CCZ_UPDATE) != null) {
            Intent i = new Intent(context, PromptCczUpdateActivity.class);
            context.startActivityForResult(i, requestCode);
            return true;
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
            String prefsKey = (type == UpdateToPrompt.Type.APK_UPDATE) ?
                    UpdateToPrompt.KEY_APK_UPDATE_TO_PROMPT : UpdateToPrompt.KEY_CCZ_UPDATE_TO_PROMPT;
            String serializedUpdate = currentApp.getAppPreferences().getString(prefsKey, "");
            if (!"".equals(serializedUpdate)) {
                byte[] updateBytes = Base64.decode(serializedUpdate, Base64.DEFAULT);
                UpdateToPrompt update;
                try {
                     update = SerializationUtil.deserialize(updateBytes, UpdateToPrompt.class);
                } catch (Exception e) {
                    // Something went wrong, so clear out whatever is there
                    Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
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
        String prefsKey = (type == UpdateToPrompt.Type.APK_UPDATE) ?
                UpdateToPrompt.KEY_APK_UPDATE_TO_PROMPT : UpdateToPrompt.KEY_CCZ_UPDATE_TO_PROMPT;
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putString(prefsKey, "").commit();
    }

}
