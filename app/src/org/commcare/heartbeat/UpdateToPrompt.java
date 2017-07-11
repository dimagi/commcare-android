package org.commcare.heartbeat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Base64;

import org.commcare.CommCareApplication;
import org.commcare.logging.AndroidLogger;
import org.commcare.utils.SerializationUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by amstone326 on 4/13/17.
 */

public class UpdateToPrompt implements Externalizable {

    public static final String KEY_CCZ_UPDATE_TO_PROMPT = "ccz-update-to-prompt";
    public static final String KEY_APK_UPDATE_TO_PROMPT = "apk-update-to-prompt";

    private String versionString;
    private int cczVersion;
    private ApkVersion apkVersion;
    private boolean isForced;
    protected Type updateType;

    public enum Type {
        APK_UPDATE(KEY_APK_UPDATE_TO_PROMPT),
        CCZ_UPDATE(KEY_CCZ_UPDATE_TO_PROMPT);

        private String prefsKey;

        Type(String s) {
            this.prefsKey = s;
        }

        protected String getPrefsKey() {
            return this.prefsKey;
        }

    }

    public UpdateToPrompt(String version, String forceString, Type type) {
        if (forceString != null) {
            this.isForced = "true".equals(forceString);
        }
        this.updateType = type;
        this.versionString = version;
        buildFromVersionString();
    }

    public UpdateToPrompt() {
        // for deserialization
    }

    private void buildFromVersionString() {
        if (this.updateType == Type.APK_UPDATE) {
            this.apkVersion = new ApkVersion(versionString);
        } else {
            this.cczVersion = Integer.parseInt(versionString);
        }
    }

    public void registerWithSystem() {
        if (isNewerThanCurrentVersion()) {
            printDebugStatement();
            writeToPrefsObject(CommCareApplication.instance().getCurrentApp().getAppPreferences());
        } else {
            // If the latest signal we're getting is that our current version is up-to-date,
            // then we should wipe any update prompt for this type that was previously stored
            UpdatePromptHelper.wipeStoredUpdate(this.updateType);
        }
    }

    private void printDebugStatement() {
        if (this.updateType == Type.APK_UPDATE) {
            System.out.println(".apk version to prompt for update set to " + apkVersion);
        } else {
            System.out.println(".ccz version to prompt for update set to " + cczVersion);
        }
        System.out.println("Is forced?: " + isForced);
    }

    public boolean isForced() {
        return isForced;
    }

    public boolean isNewerThanCurrentVersion() {
        if (this.updateType == Type.APK_UPDATE) {
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
            int currentVersion = CommCareApplication.instance().getCommCarePlatform().getCurrentProfile().getVersion();
            return currentVersion < this.cczVersion;
        }
    }

    private void writeToPrefsObject(SharedPreferences prefs) {
        try {
            byte[] serializedBytes = SerializationUtil.serialize(this);
            String serializedString = Base64.encodeToString(serializedBytes, Base64.DEFAULT);
            prefs.edit().putString(this.updateType.getPrefsKey(), serializedString).commit();
        } catch (Exception e) {
            Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                    "Error encountered while serializing UpdateToPrompt: " + e.getMessage());
        }
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        this.versionString = ExtUtil.readString(in);
        this.updateType = Type.valueOf(ExtUtil.readString(in));
        this.isForced = ExtUtil.readBool(in);
        buildFromVersionString();
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, versionString);
        ExtUtil.writeString(out, updateType.name());
        ExtUtil.writeBool(out, isForced);
    }

    public int getCczVersion() {
        return cczVersion;
    }

}
