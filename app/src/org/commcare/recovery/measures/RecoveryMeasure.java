package org.commcare.recovery.measures;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.heartbeat.ApkVersion;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.util.LogTypes;
import org.commcare.utils.AppLifecycleUtils;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.services.Logger;

/**
 * Created by amstone326 on 4/27/18.
 */

@Table(RecoveryMeasure.STORAGE_KEY)
public class RecoveryMeasure extends Persisted {

    public static final String STORAGE_KEY = "RecoveryMeasures";

    private static final String APP_REINSTALL = "app_reinstall";
    private static final String APP_REINSTALL_LOCAL = "app_reinstall_local";
    private static final String APP_UPDATE = "app_update";
    private static final String CLEAR_USER_DATA = "clear_data";
    private static final String CC_REINSTALL_NEEDED = "cc_reinstall";
    private static final String CC_UPDATE_NEEDED = "cc_update";

    public static final int STATUS_EXECUTED = 0;
    public static final int STATUS_FAILED = 1;
    public static final int STATUS_WAITING = 2;

    @Persisting(1)
    private String type;
    @Persisting(2)
    private int sequenceNumber;
    @Persisting(3)
    private String ccVersionMin;
    @Persisting(4)
    private String ccVersionMax;
    @Persisting(5)
    private int appVersionMin;
    @Persisting(6)
    private int appVersionMax;

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

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    void registerWithSystem() {
        CommCareApplication.instance().getAppStorage(RecoveryMeasure.class).write(this);
    }

    public int execute(ExecuteRecoveryMeasuresActivity activity) {
        // All recovery measures assume there is a seated app to execute upon, so check that first
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (currentApp == null) {
            return STATUS_FAILED;
        }

        switch(type) {
            case APP_REINSTALL:
                String profileRef = currentApp.getCommCarePlatform().getCurrentProfile().getAuthReference();
                SingleAppInstallation.installSingleApp(activity, profileRef);
                //AppLifecycleUtils.reinstall(currentApp);
                return STATUS_WAITING;
            case APP_REINSTALL_LOCAL:
                AppLifecycleUtils.reinstallIfLocalCczPresent(currentApp);
                return STATUS_WAITING;
            case APP_UPDATE:
                return STATUS_WAITING;
            case CLEAR_USER_DATA:
                clearDataForCurrentOrLastUser();
                return STATUS_EXECUTED;
            case CC_REINSTALL_NEEDED:
                return STATUS_EXECUTED;
            case CC_UPDATE_NEEDED:
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
