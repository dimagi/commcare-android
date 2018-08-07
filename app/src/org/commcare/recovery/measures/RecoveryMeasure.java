package org.commcare.recovery.measures;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.IntDef;
import android.support.annotation.StringDef;

import org.commcare.CommCareApplication;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.heartbeat.ApkVersion;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Created by amstone326 on 4/27/18.
 */

@Table(RecoveryMeasure.STORAGE_KEY)
public class RecoveryMeasure extends Persisted {

    public static final String STORAGE_KEY = "RecoveryMeasures";

    public static final String MEASURE_TYPE_APP_REINSTALL = "app_reinstall";
    public static final String MEASURE_TYPE_APP_UPDATE = "app_update";
    public static final String MEASURE_TYPE_CLEAR_USER_DATA = "clear_data";
    public static final String MEASURE_TYPE_CC_REINSTALL_NEEDED = "cc_reinstall";
    public static final String MEASURE_TYPE_CC_UPDATE_NEEDED = "cc_update";

    public static final int STATUS_EXECUTED = 0;
    public static final int STATUS_FAILED = 1;
    public static final int STATUS_WAITING = 2;
    public static final int STATUS_TOO_SOON = 3;

    @Persisting(1)
    private String type;
    @Persisting(2)
    private long sequenceNumber;
    @Persisting(3)
    private String ccVersionMin;
    @Persisting(4)
    private String ccVersionMax;
    @Persisting(5)
    private int appVersionMin;
    @Persisting(6)
    private int appVersionMax;
    @Persisting(7)
    private long lastAttemptTime;

    public RecoveryMeasure() {
    }

    @StringDef({MEASURE_TYPE_APP_REINSTALL, MEASURE_TYPE_APP_UPDATE,
            MEASURE_TYPE_CLEAR_USER_DATA, MEASURE_TYPE_CC_REINSTALL_NEEDED, MEASURE_TYPE_CC_UPDATE_NEEDED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface MeasureType {
    }

    @IntDef({STATUS_EXECUTED, STATUS_FAILED, STATUS_WAITING,STATUS_TOO_SOON})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RecoveryMeasureStatus {
    }

    protected RecoveryMeasure(@MeasureType String type, int sequenceNumber, String ccVersionMin,
                              String ccVersionMax, int appVersionMin, int appVersionMax) {
        this.type = type;
        this.sequenceNumber = sequenceNumber;
        this.ccVersionMin = ccVersionMin;
        this.ccVersionMax = ccVersionMax;
        this.appVersionMin = appVersionMin;
        this.appVersionMax = appVersionMax;
        this.lastAttemptTime = -1;
    }

    protected boolean newToCurrentInstallation() {
        List<RecoveryMeasure> pendingInStorage = RecoveryMeasuresHelper.getPendingRecoveryMeasuresInOrder(
                CommCareApplication.instance().getAppStorage(RecoveryMeasure.class));
        return pendingInStorage.size() == 0 ||
                sequenceNumber > pendingInStorage.get(pendingInStorage.size() - 1).sequenceNumber;
    }

    protected boolean applicableToCurrentInstallation() {
        return sequenceNumberIsNewer() && applicableToAppVersion() & applicableToCommCareVersion();
    }

    private boolean sequenceNumberIsNewer() {
        return this.sequenceNumber > HiddenPreferences.getLatestRecoveryMeasureExecuted();
    }

    private boolean applicableToAppVersion() {
        // max and min both being -1 signifies that the measure is applicable to all versions
        if (appVersionMin == -1 && appVersionMax == -1) {
            return true;
        }

        int currentAppVersion = CommCareApplication.instance()
                .getCommCarePlatform()
                .getCurrentProfile()
                .getVersion();

        return currentAppVersion >= appVersionMin && currentAppVersion <= appVersionMax;
    }

    private boolean applicableToCommCareVersion() {
        // max and min both being null signifies that the measure is applicable to all versions
        if (ccVersionMax == null && ccVersionMin == null) {
            return true;
        }

        try {
            Context c = CommCareApplication.instance();
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            ApkVersion currentVersion = new ApkVersion(pi.versionName);
            return currentVersion.compareTo(new ApkVersion(ccVersionMin)) >= 0 &&
                    currentVersion.compareTo(new ApkVersion(ccVersionMax)) <= 0;
        } catch (PackageManager.NameNotFoundException e) {
            // This should never happen, but it if it does, there's no way for us to know for sure
            // if the recovery measure is applicable, so assume it is
            Logger.log(LogTypes.TYPE_ERROR_WORKFLOW,
                    "Couldn't get current .apk version to compare with in RecoveryMeasure: "
                            + e.getMessage());
            return true;
        }
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    void registerWithSystem() {
        CommCareApplication.instance().getAppStorage(RecoveryMeasure.class).write(this);
    }

    void setLastAttemptTime(SqlStorage<RecoveryMeasure> storage) {
        this.lastAttemptTime = System.currentTimeMillis();
        storage.write(this);
    }

    boolean triedTooRecently() {
        if (lastAttemptTime == -1) {
            return false;
        }
        long timediff = System.currentTimeMillis() - this.lastAttemptTime;
        if(timediff < 0){
            timediff *= -1;
        }
        return timediff < 10000; //TODO: change threshold back
    }

    public String getType() {
        return type;
    }
}
