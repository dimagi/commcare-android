package org.commcare.engine.resource;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.PromptApkUpdateActivity;
import org.commcare.activities.TargetMismatchErrorActivity;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.core.network.CaptivePortalRedirectException;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.network.RateLimitedException;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.resources.ResourceManager;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnreliableSourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.Profile;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.ResourceEngineTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.NotificationActionButtonInfo;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

import javax.net.ssl.SSLException;

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
     * Creates a new application record in db
     * @return newly created CommCare App
     */
    private static CommCareApp createNewCommCareApp() {
        ApplicationRecord newRecord =
                new ApplicationRecord(PropertyUtils.genUUID().replace("-", ""),
                        ApplicationRecord.STATUS_UNINITIALIZED);

        return new CommCareApp(newRecord);
    }

    public static CommCareApp startAppInstallAsync(boolean shouldSleep, int taskId, CommCareTaskConnector connector,
            String installRef) {
        CommCareApp ccApp = createNewCommCareApp();
        ResourceEngineTask<ResourceEngineListener> task =
                new ResourceEngineTask<>(ccApp,
                        taskId, shouldSleep, false) {

                    @Override
                    protected void deliverResult(ResourceEngineListener receiver,
                            AppInstallStatus result) {
                        handleAppInstallResult(this, receiver, result);
                    }

                    @Override
                    protected void deliverUpdate(ResourceEngineListener receiver, int[]... update) {
                        receiver.updateResourceProgress(update[0][0], update[0][1], update[0][2]);
                    }

                    @Override
                    protected void deliverError(ResourceEngineListener receiver, Exception e) {
                        receiver.failUnknown(AppInstallStatus.UnknownFailure);
                    }
                };

        task.connect(connector);
        task.executeParallel(installRef);
        return ccApp;
    }

    public static void handleAppInstallResult(ResourceEngineTask resourceEngineTask, ResourceEngineListener receiver, AppInstallStatus result) {
        switch (result) {
            case Installed:
                receiver.reportSuccess(true);
                break;
            case UpToDate:
                receiver.reportSuccess(false);
                break;
            case MissingResourcesWithMessage:
                // fall through to more general case:
            case MissingResources:
                receiver.failMissingResource(resourceEngineTask.getMissingResourceException(), result);
                break;
            case InvalidResource:
                receiver.failInvalidResource(resourceEngineTask.getInvalidResourceException(), result);
                break;
            case InvalidReference:
                receiver.failInvalidReference(resourceEngineTask.getInvalidReferenceException(), result);
                break;
            case IncompatibleReqs:
                receiver.failBadReqs(resourceEngineTask.getVersionRequired(),
                        resourceEngineTask.getVersionAvailable(), resourceEngineTask.isMajorIsProblem());
                break;
            case NoLocalStorage:
                receiver.failWithNotification(AppInstallStatus.NoLocalStorage);
                break;
            case NoConnection:
                receiver.failWithNotification(AppInstallStatus.NoConnection);
                break;
            case BadSslCertificate:
                receiver.failWithNotification(AppInstallStatus.BadSslCertificate, NotificationActionButtonInfo.ButtonAction.LAUNCH_DATE_SETTINGS);
                break;
            case DuplicateApp:
                receiver.failWithNotification(AppInstallStatus.DuplicateApp);
                break;
            case IncorrectTargetPackage:
                receiver.failTargetMismatch();
                break;
            case ReinstallFromInvalidCcz:
                receiver.failUnknown(AppInstallStatus.ReinstallFromInvalidCcz);
                break;
            case CaptivePortal:
                receiver.failWithNotification(AppInstallStatus.CaptivePortal);
                break;
            default:
                receiver.failUnknown(AppInstallStatus.UnknownFailure);
                break;
        }
    }

    /**
     * Shows Apk update prompt to user
     * @param context current context
     * @param versionRequired apk version required
     * @param versionAvailable apk version available
     */
    public static void showApkUpdatePrompt(Context context, String versionRequired, String versionAvailable) {
        String versionMismatch = Localization.get("install.version.mismatch", new String[]{versionRequired, versionAvailable});
        Intent intent = new Intent(context, PromptApkUpdateActivity.class);
        intent.putExtra(PromptApkUpdateActivity.REQUIRED_VERSION, versionRequired);
        intent.putExtra(PromptApkUpdateActivity.CUSTOM_PROMPT_TITLE, versionMismatch);
        context.startActivity(intent);
    }

    /**
     * Show target mismatch error during CC App installation
     * @param context current context
     */
    public static void showTargetMismatchError(Context context) {
        Intent intent = new Intent(context, TargetMismatchErrorActivity.class);
        context.startActivity(intent);
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

    private static void updateProfileRef(SharedPreferences prefs,
                                        String authRef, String profileRef) {
        SharedPreferences.Editor edit = prefs.edit();
        if (authRef != null) {
            edit.putString(DEFAULT_APP_SERVER_KEY, authRef);
        } else {
            edit.putString(DEFAULT_APP_SERVER_KEY, profileRef);
        }
        edit.apply();
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

        if(exception instanceof UnreliableSourceException) {
            if (exception.getCause() instanceof CaptivePortalRedirectException) {
                Logger.log(LogTypes.TYPE_WARNING_NETWORK,
                        "Captive portal detected while installing a resource|" +
                                exception.getMessage());
                return AppInstallStatus.CaptivePortal;
            }
            Logger.log(LogTypes.TYPE_WARNING_NETWORK,
                    "A resource couldn't be found, almost certainly due to the network|" +
                            exception.getMessage());
            return AppInstallStatus.NetworkFailure;
        }

        if(exception.getCause() instanceof SSLException){
            return AppInstallStatus.BadSslCertificate;
        }

        if(exception.getCause() instanceof RateLimitedException){
            return AppInstallStatus.RateLimited;
        }

        if (exception.isMessageUseful()) {
            return AppInstallStatus.MissingResourcesWithMessage;
        } else {
            return AppInstallStatus.MissingResources;
        }
    }

    /**
     * Set app's last update attempt time to now in the shared preferences
     */
    public static void recordUpdateAttemptTime(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(HiddenPreferences.LAST_UPDATE_ATTEMPT, new Date().getTime());
        editor.apply();
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
    public static boolean isAutoUpdateInProgress(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        return prefs.getBoolean(HiddenPreferences.AUTO_UPDATE_IN_PROGRESS, false);
    }

    public static void logInstallError(Exception e, String logMessage) {
        e.printStackTrace();
        Logger.exception(logMessage, e);
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
