package org.commcare;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Base64;

import org.commcare.logging.AndroidLogger;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.ByteArrayOutputStream;
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
            CommCareHeartbeatManager.wipeStoredUpdate(this.isApkUpdate);
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
        ByteArrayOutputStream baos = null;
        DataOutputStream serializedStream = null;
        try {
            baos = new ByteArrayOutputStream();
            serializedStream = new DataOutputStream(baos);
            try {
                ExtUtil.write(serializedStream, this);
                String serializedString = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                prefs.edit().putString(
                        isApkUpdate ? KEY_APK_UPDATE_TO_PROMPT : KEY_CCZ_UPDATE_TO_PROMPT,
                        serializedString).commit();
                baos.close();
                serializedStream.close();
            } catch (IOException e) {
                Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                        "IO error encountered while serializing UpdateToPrompt: " + e.getMessage());
            }
        } finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (serializedStream != null) {
                    serializedStream.close();
                }
            } catch (IOException e) {
                // Nothing we can do
            }
        }
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        this.versionString = ExtUtil.readString(in);
        this.isApkUpdate = ExtUtil.readBool(in);
        boolean hasForceByDate = ExtUtil.readBool(in);
        if (hasForceByDate) {
            this.forceByDate = ExtUtil.readDate(in);
        }
        buildFromVersionString();
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, versionString);
        ExtUtil.writeBool(out, isApkUpdate);
        // Write this out first so that we know whether or not to try to read back a forceByDate
        // in readExternal()
        ExtUtil.writeBool(out, forceByDate != null);
        if (forceByDate != null) {
            ExtUtil.writeDate(out, forceByDate);
        }
    }
}
