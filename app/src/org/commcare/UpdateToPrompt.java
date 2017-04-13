package org.commcare;

import android.preference.PreferenceManager;

import org.javarosa.core.model.utils.DateUtils;

import java.util.Date;

/**
 * Created by amstone326 on 4/13/17.
 */

public class UpdateToPrompt {

    private int cczVersion;
    private ApkVersion apkVersion;
    private Date forceByDate;
    private boolean isApkUpdate;

    public UpdateToPrompt(String version, String forceByDate, boolean isApkUpdate) {
        this.forceByDate = DateUtils.parseDate(forceByDate);
        this.isApkUpdate = isApkUpdate;
        if (isApkUpdate) {
            this.apkVersion = new ApkVersion(version);
        } else {
            this.cczVersion = Integer.parseInt(version);
        }
    }

    public void registerWithSystem() {
        if (isApkUpdate) {
            CommCareApplication.instance().registerUpdateToPrompt(this);
        } else {
            CommCareApplication.instance().getCurrentApp().registerUpdateToPrompt(this);
        }
    }

    public int getCczVersion() {
        return cczVersion;
    }

    public ApkVersion getApkVersion() {
        return this.apkVersion;
    }
}
