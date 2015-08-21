package org.commcare.android.resource;

import android.content.SharedPreferences;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.ResourceEngineOutcomes;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareResourceManager;
import org.javarosa.core.services.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.Date;

import javax.net.ssl.SSLHandshakeException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ResourceInstallUtils {
    public static boolean isUpdateInstallReady() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        AndroidCommCarePlatform platform = app.getCommCarePlatform();
        ResourceTable upgradeTable = platform.getUpgradeResourceTable();
        return CommCareResourceManager.isTableStaged(upgradeTable);
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

        if (ResourceInstallUtils.isBadCertificateError(e)) {
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

    public static void recordUpdateAttempt(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, new Date().getTime());
        editor.commit();
    }

    public static void logInstallError(Exception e, String logMessage) {
        e.printStackTrace();

        Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                logMessage + e.getMessage());
    }

    public static String addParamsToProfileReference(final String profileRef) {
        // TODO PLM: move to commcare repo and unify with util usage of this
        // logic
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

        // If we want to be using/updating to the latest build of the
        // app (instead of latest release), add it to the query tags of
        // the profile reference
        if (DeveloperPreferences.isNewestAppVersionEnabled()) {
            if (profileUrl.getQuery() != null) {
                // url already has query strings, so add a new one to the end
                return profileRef + "&target=build";
            } else {
                return profileRef + "?target=build";
            }
        }

        return profileRef;
    }
}
