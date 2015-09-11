package org.commcare.android.tasks;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.SystemClock;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.xml.CommCareElementParser;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Vector;

import javax.net.ssl.SSLHandshakeException;

/**
 * @author ctsims
 */
public abstract class ResourceEngineTask<R>
        extends CommCareTask<String, int[], org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes, R>
        implements TableStateListener {

    public enum ResourceEngineOutcomes implements MessageTag {
        /**
         * App installed Succesfully
         */
        StatusInstalled("notification.install.installed"),

        /**
         * Missing resources could not be found during install
         */
        StatusMissing("notification.install.missing"),

        /**
         * Missing resources could not be found during install
         */
        StatusMissingDetails("notification.install.missing.withmessage"),

        /**
         * App is not compatible with current installation
         */
        StatusBadReqs("notification.install.badreqs"),

        /**
         * Unknown Error
         */
        StatusFailUnknown("notification.install.unknown"),

        /**
         * There's already an app installed
         */
        StatusFailState("notification.install.badstate"),

        /**
         * There's already an app installed
         */
        StatusNoLocalStorage("notification.install.nolocal"),

        /**
         * Install is fine
         */
        StatusUpToDate("notification.install.uptodate"),

        /**
         * Attempting to install an app that is already installed
         */
        StatusDuplicateApp("notification.install.duplicate"),

        /**
         * Certificate was bad
         */
        StatusBadCertificate("notification.install.badcert"),

        /**
         * There is no internet connectivity
         */
        StatusNoConnection("notification.install.no.connection");


        ResourceEngineOutcomes(String root) {
            this.root = root;
        }

        private final String root;

        public String getLocaleKeyBase() {
            return root;
        }

        public String getCategory() {
            return "install_update";
        }
    }

    private final CommCareApp app;

    private static final int PHASE_CHECKING = 0;
    public static final int PHASE_DOWNLOAD = 1;
    public static final int PHASE_COMMIT = 2;

    /**
     * Wait time between dialog updates in milliseconds
     */
    private static final long STATUS_UPDATE_WAIT_TIME = 1000;

    protected UnresolvedResourceException missingResourceException = null;
    protected int badReqCode = -1;
    private int phase = -1;
    private boolean upgradeMode = false;
    private final boolean startOverUpgrade;
    // This boolean is set from CommCareSetupActivity -- If we are in keep
    // trying mode for installation, we want to sleep in between attempts to
    // launch this task
    private final boolean shouldSleep;

    // last time in system millis that we updated the status dialog
    private long lastTime = 0;

    protected String vAvailable;
    protected String vRequired;
    protected boolean majorIsProblem;

    public ResourceEngineTask(boolean upgradeMode, CommCareApp app,
                              boolean startOverUpgrade, int taskId, boolean shouldSleep) {
        this.upgradeMode = upgradeMode;
        this.app = app;
        this.startOverUpgrade = startOverUpgrade;
        this.taskId = taskId;
        this.shouldSleep = shouldSleep;

        TAG = ResourceEngineTask.class.getSimpleName();
    }

    @Override
    protected ResourceEngineOutcomes doTaskBackground(String... profileRefs) {
        String profileRef = profileRefs[0];
        AndroidCommCarePlatform platform = app.getCommCarePlatform();
        SharedPreferences prefs = app.getAppPreferences();


        // Make sure we record that an attempt was started.
        Editor editor = prefs.edit();
        editor.putLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, new Date().getTime());
        editor.commit();

        app.setupSandbox();

        Logger.log(AndroidLogger.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRefs[0]);

        if (shouldSleep) {
            SystemClock.sleep(2000);
        }

        try {
            // This is replicated in the application in a few places.
            ResourceTable global = platform.getGlobalResourceTable();

            // Ok, should figure out what the state of this bad boy is.
            Resource profile = global.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

            boolean appInstalled = (profile != null &&
                    profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED);

            if (upgradeMode) {
                if (!appInstalled) {
                    return ResourceEngineOutcomes.StatusFailState;
                }
                global.setStateListener(this);
                // temporary is the upgrade table, which starts out in the
                // state that it was left after the last install- partially
                // populated if it stopped in middle, empty if the install was
                // successful
                ResourceTable temporary = platform.getUpgradeResourceTable();
                ResourceTable recovery = platform.getRecoveryTable();
                temporary.setStateListener(this);

                // When profileRef points is http, add appropriate dev flags
                URL profileUrl = null;
                try {
                    profileUrl = new URL(profileRef);
                } catch (MalformedURLException e) {
                    // profileRef couldn't be parsed as a URL, so don't worry
                    // about adding dev flags to the url's query
                }

                // If we want to be using/updating to the latest build of the
                // app (instead of latest release), add it to the query tags of
                // the profile reference
                if (DeveloperPreferences.isNewestAppVersionEnabled() &&
                        (profileUrl != null) &&
                        ("https".equals(profileUrl.getProtocol()) ||
                                "http".equals(profileUrl.getProtocol()))) {
                    if (profileUrl.getQuery() != null) {
                        // If the profileRef url already have query strings
                        // just add a new one to the end
                        profileRef = profileRef + "&target=build";
                    } else {
                        // otherwise, start off the query string with a ?
                        profileRef = profileRef + "?target=build";
                    }
                }


                // This populates the upgrade table with resources based on
                // binary files, starting with the profile file. If the new
                // profile is not a newer version, statgeUpgradeTable doesn't
                // actually pull in all the new references
                platform.stageUpgradeTable(global, temporary, recovery, profileRef, startOverUpgrade);
                Resource newProfile = temporary.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);
                if (!newProfile.isNewer(profile)) {
                    Logger.log(AndroidLogger.TYPE_RESOURCES, "App Resources up to Date");
                    return ResourceEngineOutcomes.StatusUpToDate;
                }

                phase = PHASE_CHECKING;
                // Replaces global table with temporary, or w/ recovery if
                // something goes wrong
                platform.upgrade(global, temporary, recovery);
            } else {
                // Not upgrade mode, so attempting normal install
                global.setStateListener(this);
                platform.init(profileRef, global, false);
            }

            // Initializes app resources and the app itself, including doing a check to see if this
            // app record was converted by the db upgrader. This is also where
            // CommCareApplication's currentApp gets set
            CommCareApplication._().initializeGlobalResources(app);

            // Write this App Record to storage -- needs to be performed after localizations have
            // been initialized (by initializeGlobalResources), so that getDisplayName() works
            app.writeInstalled();

            // update the current profile reference
            prefs = app.getAppPreferences();
            Editor edit = prefs.edit();
            if (platform.getCurrentProfile().getAuthReference() != null) {
                edit.putString("default_app_server",
                        platform.getCurrentProfile().getAuthReference());
            } else {
                edit.putString("default_app_server", profileRef);
            }
            edit.commit();

            return ResourceEngineOutcomes.StatusInstalled;
        } catch (LocalStorageUnavailableException e) {
            e.printStackTrace();

            Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                    "Couldn't install file to local storage|" + e.getMessage());
            return ResourceEngineOutcomes.StatusNoLocalStorage;
        } catch (UnfullfilledRequirementsException e) {
            e.printStackTrace();
            if (e.isDuplicateException()) {
                return ResourceEngineOutcomes.StatusDuplicateApp;
            } else {
                badReqCode = e.getRequirementCode();
                vAvailable = e.getAvailableVesionString();
                vRequired = e.getRequiredVersionString();
                majorIsProblem = e.getRequirementCode() == CommCareElementParser.REQUIREMENT_MAJOR_APP_VERSION;

                Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                        "App resources are incompatible with this device|" + e.getMessage());
                return ResourceEngineOutcomes.StatusBadReqs;
            }
        } catch (UnresolvedResourceException e) {
            // couldn't find a resource, which isn't good.
            e.printStackTrace();

            Throwable mExceptionCause = e.getCause();

            if (mExceptionCause instanceof SSLHandshakeException) {
                Throwable mSecondExceptionCause = mExceptionCause.getCause();
                if (mSecondExceptionCause instanceof CertificateException) {
                    return ResourceEngineOutcomes.StatusBadCertificate;
                }
            }

            missingResourceException = e;
            Logger.log(AndroidLogger.TYPE_WARNING_NETWORK,
                    "A resource couldn't be found, almost certainly due to the network|" +
                            e.getMessage());
            if (e.isMessageUseful()) {
                return ResourceEngineOutcomes.StatusMissingDetails;
            } else {
                return ResourceEngineOutcomes.StatusMissing;
            }
        } catch (Exception e) {
            e.printStackTrace();

            Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                    "Unknown error ocurred during install|" + e.getMessage());
            return ResourceEngineOutcomes.StatusFailUnknown;
        }
    }

    @Override
    public void resourceStateUpdated(ResourceTable table) {
        // if last time isn't set or is less than our spacing count, do not
        // perform status update
        if (System.currentTimeMillis() - lastTime < ResourceEngineTask.STATUS_UPDATE_WAIT_TIME) {
            return;
        }

        Vector<Resource> resources = CommCarePlatform.getResourceListFromProfile(table);

        // TODO: Better reflect upgrade status process

        int score = 0;

        for (Resource r : resources) {
            switch (r.getStatus()) {
                case Resource.RESOURCE_STATUS_UPGRADE:
                    // If we spot an upgrade after we've started the upgrade process,
                    // something now needs to be updated
                    if (phase == PHASE_CHECKING) {
                        this.phase = PHASE_DOWNLOAD;
                    }
                    score += 1;
                    break;
                case Resource.RESOURCE_STATUS_INSTALLED:
                    score += 1;
                    break;
                default:
                    score += 0;
                    break;
            }
        }
        lastTime = System.currentTimeMillis();
        incrementProgress(score, resources.size());
    }

    @Override
    public void incrementProgress(int complete, int total) {
        this.publishProgress(new int[]{complete, total, phase});
    }
}