package org.commcare.recovery.measures;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.commcare.CommCareApplication;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.heartbeat.ApkVersion;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

/**
 * Created by amstone326 on 4/27/18.
 */

@Table(RecoveryMeasure.STORAGE_KEY)
public class RecoveryMeasure extends Persisted {

    public static final String STORAGE_KEY = "recovery-measures";
    private static final String META_SEQ_NUM = "SEQUENCE_NUMBER";

    public static final String APP_REINSTALL = "app_reinstall";
    public static final String APP_UPDATE = "app_update";
    public static final String CLEAR_USER_DATA = "clear_data";
    public static final String CC_REINSTALL_NEEDED = "cc_reinstall";
    public static final String CC_UPDATE_NEEDED = "cc_update";

    @Persisting(1)
    private String type;
    @MetaField(META_SEQ_NUM)
    @Persisting(2)
    private final int sequenceNumber;
    @Persisting(3)
    private final ApkVersion ccVersionMin;
    @Persisting(4)
    private final ApkVersion ccVersionMax;
    @Persisting(5)
    private final int appVersionMin;
    @Persisting(6)
    private final int appVersionMax;

    public RecoveryMeasure(String type, int sequenceNumber, String ccVersionMin,
                              String ccVersionMax, int appVersionMin, int appVersionMax) {
        this.type = type;
        this.sequenceNumber = sequenceNumber;
        this.ccVersionMin = new ApkVersion(ccVersionMin);
        this.ccVersionMax = new ApkVersion(ccVersionMax);
        this.appVersionMin = appVersionMin;
        this.appVersionMax = appVersionMax;
    }

    public boolean applicableToCurrentInstallation() {
        return sequenceNumberIsNewer() && applicableToAppVersion() & applicableToCommCareVersion();
    }

    private boolean sequenceNumberIsNewer() {
        return this.sequenceNumber > HiddenPreferences.getLatestRecoveryMeasureExecuted();
    }

    private boolean applicableToAppVersion() {
        int currentAppVersion = CommCareApplication.instance().getCommCarePlatform()
                .getCurrentProfile().getVersion();
        return currentAppVersion >= this.appVersionMin && currentAppVersion <= this.appVersionMax;
    }

    private boolean applicableToCommCareVersion() {
        try {
            Context c = CommCareApplication.instance();
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            ApkVersion currentVersion = new ApkVersion(pi.versionName);
            return currentVersion.compareTo(this.ccVersionMin) >= 0 &&
                    currentVersion.compareTo(this.ccVersionMax) <= 0;
        } catch (PackageManager.NameNotFoundException e) {
            // This should never happen, but it if it does, there's no way for us to know for sure
            // if the recovery measure is applicable, so assume it is
            Logger.log(LogTypes.TYPE_ERROR_WORKFLOW,
                    "Couldn't get current .apk version to compare with in RecoveryMeasure: "
                            + e.getMessage());
            return true;
        }
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void registerWithSystem() {
        CommCareApplication.instance().getAppStorage(RecoveryMeasure.class).write(this);
    }

    public boolean execute() {
        switch(type) {

        }
        return false;
    }
}
