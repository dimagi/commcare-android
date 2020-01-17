package org.commcare.android.javarosa;

import android.os.Build;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.preferences.DevSessionRestorer;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * @author ctsims
 */
public class AndroidLogEntry extends LogEntry implements Persistable, IMetaData {

    public static final String STORAGE_KEY = "commcarelogs";

    protected int appBuildNumber;
    protected String androidVersion;
    protected String deviceModel;
    protected String readableSessionString;
    protected String serializedSessionString;
    protected String appId;
    protected String userId;

    private static final String META_TYPE = "type";
    private static final String META_DATE = "date";

    private int recordId = -1;

    /**
     * Serialization only
     */
    public AndroidLogEntry() {

    }

    public AndroidLogEntry(String type, String message, Date date) {
        super(type, message, date);
        appBuildNumber = ReportingUtils.getAppBuildNumber();
        androidVersion = Build.VERSION.RELEASE;
        deviceModel = Build.MODEL;
        readableSessionString = ReportingUtils.getCurrentSession();
        serializedSessionString = DevSessionRestorer.getSerializedSessionString();
        appId = ReportingUtils.getAppId();
        userId = CommCareApplication.instance().getCurrentUserId();
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        recordId = ExtUtil.readInt(in);
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
        ExtUtil.writeNumeric(out, recordId);
        super.writeExternal(out);
        ExtUtil.writeNumeric(out, appBuildNumber);
        ExtUtil.writeString(out, androidVersion);
        ExtUtil.writeString(out, deviceModel);
        ExtUtil.writeString(out, readableSessionString);
        ExtUtil.writeString(out, serializedSessionString);
        ExtUtil.writeString(out, appId);
        ExtUtil.writeString(out, userId);
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
    public Date getTime() {
        return time;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String[] getMetaDataFields() {
        return new String[]{META_TYPE, META_DATE};
    }

    @Override
    public Object getMetaData(String fieldName) {
        if (META_DATE.equals(fieldName)) {
            return DateUtils.formatDate(time, DateUtils.FORMAT_ISO8601);
        } else if (META_TYPE.equals(fieldName)) {
            return type;
        }
        throw new IllegalArgumentException("No metadata field " + fieldName + " for Log Entry Cache models");
    }

    @Override
    public void setID(int ID) {
        this.recordId = ID;
    }

    @Override
    public int getID() {
        return this.recordId;
    }
}
