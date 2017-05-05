package org.commcare.heartbeat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Base64;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.PromptUpdateActivity;
import org.commcare.logging.AndroidLogger;
import org.commcare.utils.SerializationUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Created by amstone326 on 4/13/17.
 */

public class UpdateToPrompt implements Externalizable {

    public static final String KEY_CCZ_UPDATE_TO_PROMPT = "ccz-update-to-prompt";
    public static final String KEY_APK_UPDATE_TO_PROMPT = "apk-update-to-prompt";

    private String versionString;
    private int cczVersion;
    private ApkVersion apkVersion;
    private Date forceByDate;
    protected boolean isApkUpdate;

    public UpdateToPrompt(String version, String forceByDate, boolean isApkUpdate) {
        if (forceByDate != null) {
            this.forceByDate = DateUtils.parseDate(forceByDate);
        }
        this.isApkUpdate = isApkUpdate;
        this.versionString = version;
        buildFromVersionString();
    }

    public UpdateToPrompt() {
        // for deserialization
    }

    private void buildFromVersionString() {
        if (isApkUpdate) {
            this.apkVersion = new ApkVersion(versionString);
        } else {
            this.cczVersion = Integer.parseInt(versionString);
        }
    }

    public boolean isPastForceByDate() {
        return forceByDate != null && (forceByDate.getTime() < System.currentTimeMillis());
    }

    public void registerWithSystem() {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (isNewerThanCurrentVersion(currentApp)) {
            printDebugStatement();
            writeToPrefsObject(currentApp.getAppPreferences());
        } else {
            // If the latest signal we're getting is that our current version is up-to-date,
            // then we should wipe any update prompt for this type that was previously stored
            wipeStoredUpdate(this.isApkUpdate);
        }
    }

    private void printDebugStatement() {
        if (isApkUpdate) {
            System.out.println(".apk version to prompt for update set to " + apkVersion);
        } else {
            System.out.println(".ccz version to prompt for update set to " + cczVersion);
        }
        if (this.forceByDate != null) {
            System.out.println("force-by date is " + forceByDate);
        }
    }

    public boolean isNewerThanCurrentVersion(CommCareApp currentApp) {
        if (isApkUpdate) {
            try {
                Context c = CommCareApplication.instance();
                PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
                ApkVersion currentVersion = new ApkVersion(pi.versionName);
                return currentVersion.compareTo(this.apkVersion) < 0;
            } catch (PackageManager.NameNotFoundException e) {
                // This shouldn't happen, but it if it does, there's no way for us to know if the
                // update version is newer, so don't prompt
                Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                        "Couldn't get current .apk version to compare with in UpdateToPrompt: "
                                + e.getMessage());
                return false;
            }
        } else {
            int currentVersion = currentApp.getCommCarePlatform().getCurrentProfile().getVersion();
            return currentVersion < this.cczVersion;
        }
    }

    private void writeToPrefsObject(SharedPreferences prefs) {
        try {
            byte[] serializedBytes = SerializationUtil.serialize(this);
            String serializedString = Base64.encodeToString(serializedBytes, Base64.DEFAULT);
            prefs.edit().putString(
                    isApkUpdate ? KEY_APK_UPDATE_TO_PROMPT : KEY_CCZ_UPDATE_TO_PROMPT,
                    serializedString).commit();
        } catch (Exception e) {
            Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                    "Error encountered while serializing UpdateToPrompt: " + e.getMessage());
        }
    }

    /**
     * @return - If the user was prompted to update
     */
    public static boolean promptForUpdateIfNeeded(Activity context) {
        UpdateToPrompt cczUpdate = getCurrentUpdateToPrompt(false);
        UpdateToPrompt apkUpdate = getCurrentUpdateToPrompt(true);
        if (cczUpdate != null || apkUpdate != null) {
            Intent i = new Intent(context, PromptUpdateActivity.class);
            context.startActivity(i);
            return true;
        }
        return false;
    }

    /**
     * @return an UpdateToPrompt that has been stored in SharedPreferences and is still relevant
     * (i.e. the user hasn't updated to or past this version since we stored it)
     */
    public static UpdateToPrompt getCurrentUpdateToPrompt(boolean forApkUpdate) {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (currentApp != null) {
            String prefsKey = forApkUpdate ?
                    UpdateToPrompt.KEY_APK_UPDATE_TO_PROMPT : UpdateToPrompt.KEY_CCZ_UPDATE_TO_PROMPT;
            String serializedUpdate = currentApp.getAppPreferences().getString(prefsKey, "");
            if (!"".equals(serializedUpdate)) {
                try {
                    byte[] updateBytes = Base64.decode(serializedUpdate, Base64.DEFAULT);
                    UpdateToPrompt update = SerializationUtil.deserialize(updateBytes, UpdateToPrompt.class);
                    if (update.isNewerThanCurrentVersion(currentApp)) {
                        return update;
                    } else {
                        // The update we had stored is no longer relevant, so wipe it and return nothing
                        wipeStoredUpdate(forApkUpdate);
                        return null;
                    }
                } catch (Exception e) {
                    // Something went wrong, so clear out whatever is there
                    Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                            "IO error encountered while de-serializing saved UpdateToPrompt: "
                                    + e.getMessage());
                    wipeStoredUpdate(forApkUpdate);
                }
            }
        }
        return null;
    }

    public static void wipeStoredUpdate(boolean forApkUpdate) {
        String prefsKey = forApkUpdate ?
                UpdateToPrompt.KEY_APK_UPDATE_TO_PROMPT : UpdateToPrompt.KEY_CCZ_UPDATE_TO_PROMPT;
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putString(prefsKey, "").commit();
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        this.versionString = ExtUtil.readString(in);
        this.isApkUpdate = ExtUtil.readBool(in);
        this.forceByDate = (Date)ExtUtil.read(in, new ExtWrapNullable(Date.class), pf);
        buildFromVersionString();
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, versionString);
        ExtUtil.writeBool(out, isApkUpdate);
        ExtUtil.write(out, new ExtWrapNullable(forceByDate));
    }
}
