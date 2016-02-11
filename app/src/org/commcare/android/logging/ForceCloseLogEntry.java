package org.commcare.android.logging;

import android.os.Build;

import org.commcare.android.session.DevSessionRestorer;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Created by amstone326 on 2/11/16.
 */
public class ForceCloseLogEntry extends AndroidLogEntry {

    private int appBuildNumber;
    private String username;
    private String ccVersionString;
    private String domain;
    private String androidVersion;
    private String deviceModel;
    private String readableSessionString;
    private String serializedSessionString;

    public ForceCloseLogEntry(String stackTrace) {
        super(AndroidLogger.TYPE_FORCECLOSE, stackTrace, new Date());
        appBuildNumber = ReportingUtils.getAppBuildNumber();
        username = ReportingUtils.getUser();
        ccVersionString = ReportingUtils.getVersion();
        domain = ReportingUtils.getDomain();
        androidVersion = Build.VERSION.RELEASE;
        deviceModel = Build.MODEL;
        readableSessionString = ReportingUtils.getCurrentSession();
        serializedSessionString = DevSessionRestorer.getSerializedSessionString();
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        super.readExternal(in, pf);
        appBuildNumber = ExtUtil.readInt(in);
        username = ExtUtil.readString(in);
        ccVersionString = ExtUtil.readString(in);
        domain = ExtUtil.readString(in);
        androidVersion = ExtUtil.readString(in);
        deviceModel = ExtUtil.readString(in);
        readableSessionString = ExtUtil.readString(in);
        serializedSessionString = ExtUtil.readString(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeNumeric(out, appBuildNumber);
        ExtUtil.writeString(out, username);
        ExtUtil.writeString(out, ccVersionString);
        ExtUtil.writeString(out, domain);
        ExtUtil.writeString(out, androidVersion);
        ExtUtil.writeString(out, deviceModel);
        ExtUtil.writeString(out, readableSessionString);
        ExtUtil.writeString(out, serializedSessionString);
    }

}
