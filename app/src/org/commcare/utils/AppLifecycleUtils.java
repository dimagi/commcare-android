package org.commcare.utils;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.app.DatabaseAppOpenHelper;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.provider.ProviderUtils;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.io.File;

import androidx.work.WorkManager;

/**
 * Created by amstone326 on 5/10/18.
 */

public class AppLifecycleUtils {

    /**
     * Completes a full uninstall of the CC app that the given ApplicationRecord represents.
     * This method should be idempotent and should be capable of completing an uninstall
     * regardless of previous failures
     */
    public static void uninstall(ApplicationRecord record) {
        DataChangeLogger.log(new DataChangeLog.CommCareAppUninstall(record.getDisplayName(), record.getVersionNumber()));

        CommCareApplication ccInstance = CommCareApplication.instance();
        CommCareApp app = new CommCareApp(record);

        // 1) If the app we are uninstalling is the currently-seated app, tear down its sandbox
        if (ccInstance.isSeated(record)) {
            ccInstance.getCurrentApp().teardownSandbox();
            ccInstance.unseat(record);
        }

        // 2) Set record's status to delete requested, so we know if we have left it in a bad
        // state later
        record.setStatus(ApplicationRecord.STATUS_DELETE_REQUESTED);
        ccInstance.getGlobalStorage(ApplicationRecord.class).write(record);

        // cancel all Workmanager tasks for this app
        WorkManager.getInstance(CommCareApplication.instance()).cancelAllWorkByTag(record.getUniqueId());

        // 3) Delete the directory containing all of this app's resources
        if (!FileUtil.deleteFileOrDir(app.storageRoot())) {
            Logger.log(LogTypes.TYPE_RESOURCES, "App storage root was unable to be " +
                    "deleted during app uninstall. Aborting uninstall process for now.");
            return;
        }

        // 4) Delete all the user databases associated with this app
        SqlStorage<UserKeyRecord> userDatabase = app.getStorage(UserKeyRecord.class);
        for (UserKeyRecord user : userDatabase) {
            File f = ccInstance.getDatabasePath(DatabaseUserOpenHelper.getDbName(user.getUuid()));
            if (!FileUtil.deleteFileOrDir(f)) {
                Logger.log(LogTypes.TYPE_RESOURCES, "A user database was unable to be " +
                        "deleted during app uninstall. Aborting uninstall process for now.");
                // If we failed to delete a file, it is likely because there is an open pointer
                // to that db still in use, so stop the uninstall for now, and rely on it to
                // complete the next time the app starts up
                return;
            }
        }

        // 5) Delete the forms database for this app
        File formsDb = ccInstance.getDatabasePath(ProviderUtils.getProviderDbName(
                ProviderUtils.ProviderType.FORMS,
                app.getAppRecord().getApplicationId()));
        if (!FileUtil.deleteFileOrDir(formsDb)) {
            Logger.log(LogTypes.TYPE_RESOURCES, "The app's forms database was unable to be " +
                    "deleted during app uninstall. Aborting uninstall process for now.");
            return;
        }

        // 6) Delete the instances database for this app
        File instancesDb = ccInstance.getDatabasePath(ProviderUtils.getProviderDbName(
                ProviderUtils.ProviderType.INSTANCES,
                app.getAppRecord().getApplicationId()));
        if (!FileUtil.deleteFileOrDir(instancesDb)) {
            Logger.log(LogTypes.TYPE_RESOURCES, "The app's instances database was unable to" +
                    " be deleted during app uninstall. Aborting uninstall process for now.");
            return;
        }

        // 7) Delete the app database
        File f = ccInstance.getDatabasePath(DatabaseAppOpenHelper.getDbName(app.getAppRecord().getApplicationId()));
        if (!FileUtil.deleteFileOrDir(f)) {
            Logger.log(LogTypes.TYPE_RESOURCES, "The app database was unable to be deleted" +
                    "during app uninstall. Aborting uninstall process for now.");
            return;
        }

        // 8) Delete the ApplicationRecord
        ccInstance.getGlobalStorage(ApplicationRecord.class).remove(record.getID());
    }
}
