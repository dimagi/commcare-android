package org.commcare.recovery.measures;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.heartbeat.ApkVersion;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.resources.model.Resource;
import org.commcare.util.LogTypes;
import org.commcare.utils.AppLifecycleUtils;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.javarosa.core.services.Logger;

import java.util.List;

/**
 * Created by amstone326 on 4/27/18.
 */

@Table(RecoveryMeasure.STORAGE_KEY)
public class RecoveryMeasure extends Persisted {

    public static final String STORAGE_KEY = "RecoveryMeasures";
    private static final int ONE_HOUR_IN_MILLIS = 60 * 60 * 1000;

    private static final String APP_REINSTALL_OTA = "app_reinstall_ota";
    private static final String APP_REINSTALL_LOCAL = "app_reinstall_local";
    private static final String APP_UPDATE = "app_update";
    private static final String CLEAR_USER_DATA = "clear_data";
    private static final String CC_REINSTALL_NEEDED = "cc_reinstall";
    private static final String CC_UPDATE_NEEDED = "cc_update";

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

    protected RecoveryMeasure(String type, int sequenceNumber, String ccVersionMin,
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
        List<RecoveryMeasure> pendingInStorage = StorageUtils.getPendingRecoveryMeasuresInOrder(
                CommCareApplication.instance().getAppStorage(RecoveryMeasure.class));
        return pendingInStorage.size() == 0 ||
                this.sequenceNumber > pendingInStorage.get(pendingInStorage.size()-1).sequenceNumber;
    }

    protected boolean applicableToCurrentInstallation() {
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
            return currentVersion.compareTo(new ApkVersion(this.ccVersionMin)) >= 0 &&
                    currentVersion.compareTo(new ApkVersion(this.ccVersionMax)) <= 0;
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
        return System.currentTimeMillis() - this.lastAttemptTime < 10000; //TODO: change threshold back
    }

    public int execute(ExecuteRecoveryMeasuresActivity activity) {
        if (triedTooRecently()) {
            return STATUS_TOO_SOON;
        }
        // All recovery measures assume there is a seated app to execute upon, so check that first
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (currentApp == null) {
            return STATUS_FAILED;
        }

        switch(type) {
            case APP_REINSTALL_OTA:
                // NOT WORKING
                String profileRef = currentApp.getCommCarePlatform().getCurrentProfile().getAuthReference();
                CommCareApp newAppInstall = CommCareSetupActivity.getShellCommCareApp();
                if (SingleAppInstallation.prepareResourcesForSingleApp(newAppInstall, profileRef,
                        Resource.RESOURCE_AUTHORITY_REMOTE)) {
                    AppLifecycleUtils.uninstall(currentApp.getAppRecord());
                    SingleAppInstallation.installSingleApp(activity, profileRef, true, currentApp);
                    return STATUS_WAITING;
                }
                return STATUS_FAILED;
            case APP_REINSTALL_LOCAL:
                // NOT IMPLEMENTED
                AppLifecycleUtils.reinstallIfLocalCczPresent(currentApp);
                return STATUS_WAITING;
            case APP_UPDATE:
                CommCareApplication.startAutoUpdate(activity,true, activity);
                return STATUS_WAITING;
            case CLEAR_USER_DATA:
                clearDataForCurrentOrLastUser();
                return STATUS_EXECUTED;
            case CC_REINSTALL_NEEDED:
                // NOT IMPLEMENTED
                return STATUS_EXECUTED;
            case CC_UPDATE_NEEDED:
                UpdatePromptHelper.promptUpdateForRecoveryMeasure(activity);
                return STATUS_EXECUTED;

        }
        return STATUS_FAILED;
    }

    private static void clearDataForCurrentOrLastUser() {
        try {
            CommCareApplication.instance().getSession();
            AppUtils.clearUserData();
        } catch (SessionUnavailableException e) {
            String lastUser = CommCareApplication.instance().getCurrentApp().getAppPreferences().
                    getString(HiddenPreferences.LAST_LOGGED_IN_USER, null);
            if (lastUser != null) {
                AppUtils.wipeSandboxForUser(lastUser);
            }
        }
    }
}
