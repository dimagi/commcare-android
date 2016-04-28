package org.commcare.android.logging;

import android.os.Build;

import org.commcare.CommCareApplication;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.logging.AndroidLogger;
import org.commcare.preferences.DevSessionRestorer;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Log entry for force closes, capturing the app build number, android version, device model,
 * readable session string, and serialized session string.
 *
 * @author Aliza Stone
 */
public class ForceCloseLogEntry extends AndroidLogEntry {

    public static final String STORAGE_KEY = "forcecloses";

    private int appBuildNumber;
    private String androidVersion;
    private String deviceModel;
    private String readableSessionString;
    private String serializedSessionString;
    private String appId;
    private String userId;

    /**
     * Serialization only
     */
    public ForceCloseLogEntry() {

    }

    public ForceCloseLogEntry(String stackTrace) {
        super(AndroidLogger.TYPE_FORCECLOSE, stackTrace, new Date());
        appBuildNumber = ReportingUtils.getAppBuildNumber();
        androidVersion = Build.VERSION.RELEASE;
        deviceModel = Build.MODEL;
        readableSessionString = ReportingUtils.getCurrentSession();
        serializedSessionString = DevSessionRestorer.getSerializedSessionString();
        appId = ReportingUtils.getAppId();
        userId = CommCareApplication._().getCurrentUserId();
    }

    public int getAppBuildNumber() {
        return appBuildNumber;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public String getReadableSession() {
        return readableSessionString;
    }

    public String getSerializedSessionString() {
        return serializedSessionString;
    }

    public String getAppId() {
        return appId;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        super.readExternal(in, pf);
        appBuildNumber = ExtUtil.readInt(in);
        androidVersion = ExtUtil.readString(in);
        deviceModel = ExtUtil.readString(in);
        readableSessionString = ExtUtil.readString(in);
        serializedSessionString = ExtUtil.readString(in);
        appId = ExtUtil.readString(in);
        userId = ExtUtil.readString(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeNumeric(out, appBuildNumber);
        ExtUtil.writeString(out, androidVersion);
        ExtUtil.writeString(out, deviceModel);
        ExtUtil.writeString(out, readableSessionString);
        ExtUtil.writeString(out, serializedSessionString);
        ExtUtil.writeString(out, appId);
        ExtUtil.writeString(out, userId);
    }

}
