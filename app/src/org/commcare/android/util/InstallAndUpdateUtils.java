package org.commcare.android.util;

import android.content.SharedPreferences;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.ResourceEngineOutcomes;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareResourceManager;
import org.javarosa.core.services.Logger;

import java.security.cert.CertificateException;
import java.util.Date;

import javax.net.ssl.SSLHandshakeException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class InstallAndUpdateUtils {
    public static boolean isUpgradeInstallReady() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        AndroidCommCarePlatform platform = app.getCommCarePlatform();
        ResourceTable upgradeTable = platform.getUpgradeResourceTable();
        return CommCareResourceManager.isUpgradeStaged(upgradeTable);
    }

    public static int upgradeTableVersion() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        AndroidCommCarePlatform platform = app.getCommCarePlatform();

        ResourceTable temporary = platform.getUpgradeResourceTable();

        Resource temporaryProfile = temporary.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);
        if (temporaryProfile == null) {
            return -1;
        }
        return temporaryProfile.getVersion();
    }

    public static void initAndCommitApp(CommCareApp app,
                                        String profileRef) {
        // Initializes app resources and the app itself, including doing a
        // check to see if this app record was converted by the db upgrader
        CommCareApplication._().initializeGlobalResources(app);

        // Write this App Record to storage -- needs to be performed after
        // localizations have been initialized (by
        // initializeGlobalResources), so that getDisplayName() works
        app.writeInstalled();

        String authRef = app.getCommCarePlatform().getCurrentProfile().getAuthReference();

        updateProfileRef(app.getAppPreferences(), authRef, profileRef);
    }

    private static void updateProfileRef(SharedPreferences prefs, String authRef, String profileRef) {
        SharedPreferences.Editor edit = prefs.edit();
        if (authRef != null) {
            edit.putString(ResourceEngineTask.DEFAULT_APP_SERVER, authRef);
        } else {
            edit.putString(ResourceEngineTask.DEFAULT_APP_SERVER, profileRef);
        }
        edit.commit();
    }

    public static ResourceEngineOutcomes processUnresolvedResource(UnresolvedResourceException e) {
        // couldn't find a resource, which isn't good.
        e.printStackTrace();

        if (InstallAndUpdateUtils.isBadCertificateError(e)) {
            return ResourceEngineOutcomes.StatusBadCertificate;
        }

        Logger.log(AndroidLogger.TYPE_WARNING_NETWORK,
                "A resource couldn't be found, almost certainly due to the network|" +
                        e.getMessage());
        if (e.isMessageUseful()) {
            return ResourceEngineOutcomes.StatusMissingDetails;
        } else {
            return ResourceEngineOutcomes.StatusMissing;
        }
    }

    public static boolean isBadCertificateError(UnresolvedResourceException e) {
        Throwable mExceptionCause = e.getCause();

        if (mExceptionCause instanceof SSLHandshakeException) {
            Throwable mSecondExceptionCause = mExceptionCause.getCause();
            if (mSecondExceptionCause instanceof CertificateException) {
                return true;
            }
        }
        return false;
    }

    public static void recordUpdateAttempt(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, new Date().getTime());
        editor.commit();
    }

    public static void logInstallError(Exception e, String logMessage) {
        e.printStackTrace();

        Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                logMessage + e.getMessage());
    }
}
