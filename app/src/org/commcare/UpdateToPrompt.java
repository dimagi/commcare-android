package org.commcare;

import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.util.externalizable.ExtUtil;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Created by amstone326 on 4/13/17.
 */

public class UpdateToPrompt {

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

    private void buildFromVersionString() {
        if (isApkUpdate) {
            this.apkVersion = new ApkVersion(versionString);
        } else {
            this.cczVersion = Integer.parseInt(versionString);
        }
    }

    public void registerWithSystem() {
        CommCareApplication.instance().getCurrentApp().registerUpdateToPrompt(this);
    }

    public int getCczVersion() {
        return cczVersion;
    }

    public ApkVersion getApkVersion() {
        return this.apkVersion;
    }

    public void writeToOutputStream(DataOutputStream outputStream) throws IOException {
        ExtUtil.writeString(outputStream, versionString);
        ExtUtil.writeBool(outputStream, isApkUpdate);
        ExtUtil.write(outputStream, forceByDate);
    }
}
