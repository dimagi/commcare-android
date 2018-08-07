package org.commcare.engine.resource;

import android.content.SharedPreferences;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.preferences.ServerUrls;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.resources.ResourceManager;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.Profile;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.services.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.Date;

import javax.net.ssl.SSLHandshakeException;

/**
 * Helpers to track app state and handle errors for installation and updates.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ResourceInstallUtils {
    private static final String DEFAULT_APP_SERVER_KEY = ServerUrls.PREFS_APP_SERVER_KEY;

    /**
     * @return Is the current app's designated upgrade table staged and ready
     * for installation?
     */
    public static boolean isUpdateReadyToInstall() {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        AndroidCommCarePlatform platform = app.getCommCarePlatform();
        ResourceTable upgradeTable = platform.getUpgradeResourceTable();
        return ResourceManager.isTableStagedForUpgrade(upgradeTable);
    }

    /**
     * @return Version from profile in the app's upgrade table; -1 if upgrade
     * profile not found.
     */
    public static int upgradeTableVersion() {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        AndroidCommCarePlatform platform = app.getCommCarePlatform();

        ResourceTable upgradeTable = platform.getUpgradeResourceTable();

        Resource upgradeProfile =
                upgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);
        if (upgradeProfile == null) {
            return -1;
        }
        return upgradeProfile.getVersion();
    }

    /**
     * Initialize app's resources and database, write the app record, and
     * overwrite app preference's profile reference with the authoritative
     * reference if present.
     */
    public static void initAndCommitApp(CommCareApp app) {
        // use the profile reference currently stored as a backup to the
        // authorative reference.
        SharedPreferences prefs = app.getAppPreferences();
        String profileRef = prefs.getString(DEFAULT_APP_SERVER_KEY, null);

        initAndCommitApp(app, profileRef);
    }

    /**
     * Initialize app's resources and database, write the app record, and store
     * reference to profile in app's preferences.
     *
     * @param profileRef Store backup reference to profile if authoritative
     *                   reference isn't present in the app's profile
     */
    public static void initAndCommitApp(CommCareApp currentApp,
                                        String profileRef) {
        // Initializes app resources and the app itself, including doing a
        // check to see if this app record was converted by the db upgrader
        CommCareApplication.instance().initializeGlobalResources(currentApp);

        // Write this App Record to storage -- needs to be performed after
        // localizations have been initialized (by
        // initializeGlobalResources), so that getDisplayName() works
        currentApp.writeInstalled();

        Profile profile = currentApp.getCommCarePlatform().getCurrentProfile();
        String authRef = profile.getAuthReference();

        updateProfileRef(currentApp.getAppPreferences(), authRef, profileRef);
    }

    public static void updateProfileRef(SharedPreferences prefs,
                                        String authRef, String profileRef) {
        SharedPreferences.Editor edit = prefs.edit();
        if (authRef != null) {
            edit.putString(DEFAULT_APP_SERVER_KEY, authRef);
        } else {
            edit.putString(DEFAULT_APP_SERVER_KEY, profileRef);
        }
        edit.commit();
    }

    /**
     * Handle exception that occurs when a resource can't be found during an
     * install or update
     *
     * @return Appropriate failure status based on exception properties
     */
    public static AppInstallStatus processUnresolvedResource(UnresolvedResourceException exception) {
        // couldn't find a resource, which isn't good.
        exception.printStackTrace();

        if (ResourceInstallUtils.isBadCertificateError(exception)) {
            return AppInstallStatus.BadCertificate;
        }

        Logger.log(LogTypes.TYPE_WARNING_NETWORK,
                "A resource couldn't be found, almost certainly due to the network|" +
                        exception.getMessage());
        if (exception.isMessageUseful()) {
            return AppInstallStatus.MissingResourcesWithMessage;
        } else {
            return AppInstallStatus.MissingResources;
        }
    }

    private static boolean isBadCertificateError(UnresolvedResourceException e) {
        Throwable mExceptionCause = e.getCause();

        if (mExceptionCause instanceof SSLHandshakeException) {
            Throwable mSecondExceptionCause = mExceptionCause.getCause();
            if (mSecondExceptionCause instanceof CertificateException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set app's last update attempt time to now in the shared preferences
     */
    public static void recordUpdateAttemptTime(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(HiddenPreferences.LAST_UPDATE_ATTEMPT, new Date().getTime());
        editor.commit();
    }

    /**
     * Record that an auto-update has started so that we can resume checking
     * for updates if logged out before the check has completed.
     */
    public static void recordAutoUpdateStart(CommCareApp app) {
        updateAutoUpdateInProgressPref(app, true);
    }

    /**
     * Record that auto-updating has finished or been cancelled from too many
     * retries. Used upon login to know whether to resume an auto-update check.
     */
    public static void recordAutoUpdateCompletion(CommCareApp app) {
        updateAutoUpdateInProgressPref(app, false);
    }

    private static void updateAutoUpdateInProgressPref(CommCareApp app, boolean value) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(HiddenPreferences.AUTO_UPDATE_IN_PROGRESS, value);
        editor.commit();
    }

    /**
     * @return True if an auto-update has been registered as in-progress.
     */
    public static boolean shouldAutoUpdateResume(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        return prefs.getBoolean(HiddenPreferences.AUTO_UPDATE_IN_PROGRESS, false);
    }

    public static void logInstallError(Exception e, String logMessage) {
        e.printStackTrace();

        Logger.log(LogTypes.TYPE_ERROR_WORKFLOW,
                logMessage + e.getMessage());
    }

    /**
     * Add url query parameters to profile reference based on preference settings.
     * For instance, to point the reference to the latest app build instead of
     * the latest release.
     */
    public static String addParamsToProfileReference(String profileRef) {
        URL profileUrl;
        try {
            profileUrl = new URL(profileRef);
        } catch (MalformedURLException e) {
            // don't add url query params to non-url profile references
            return profileRef;
        }

        if (!("https".equals(profileUrl.getProtocol()) ||
                "http".equals(profileUrl.getProtocol()))) {
            return profileRef;
        }

        String targetParam = MainConfigurablePreferences.getUpdateTargetParam();
        if (!"".equals(targetParam)) {
            if (profileUrl.getQuery() != null) {
                // url already has query strings, so add a new one to the end
                profileRef += "&target=" + targetParam;
            } else {
                profileRef += "?target=" + targetParam;
            }
        }

        String username;
        try {
            username = CommCareApplication.instance().getRecordForCurrentUser().getUsername();
            if (username != null & !"".equals(username)) {
                if (profileUrl.getQuery() != null) {
                    profileRef += "&username=" + username;
                } else {
                    profileRef += "?username=" + username;
                }
            }
        } catch (SessionUnavailableException e) {
            // Must be updating from the app manager, in which case we don't have a current user
        }

        return profileRef;
    }

    /**
     * @return default profile reference stored in the app's shared preferences
     */
    public static String getDefaultProfileRef() {
        if (CommCareApplication.instance().isConsumerApp()) {
            return SingleAppInstallation.SINGLE_APP_REFERENCE;
        } else {
            CommCareApp app = CommCareApplication.instance().getCurrentApp();
            SharedPreferences prefs = app.getAppPreferences();
            return prefs.getString(DEFAULT_APP_SERVER_KEY, null);
        }
    }

    /**
     * @return CommCare App Profile url without query params
     */
    public static String getProfileReference() {
        String profileRef = getDefaultProfileRef();
        return profileRef.split("\\?")[0];
    }
}
