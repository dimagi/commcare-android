package org.commcare.android.tasks;

import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Vector;

import javax.net.ssl.SSLHandshakeException;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.xml.CommCareElementParser;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.Logger;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.SystemClock;

/**
 * @author ctsims
 */
public abstract class ResourceEngineTask<R>
        extends CommCareTask<String, int[], ResourceEngineOutcomes, R>
        implements TableStateListener {

    private final CommCareApp app;

    private static final int PHASE_CHECKING = 0;
    public static final int PHASE_DOWNLOAD = 1;

    /**
     * Wait time between dialog updates in milliseconds
     */
    private static final long STATUS_UPDATE_WAIT_TIME = 1000;

    public static final String DEFAULT_APP_SERVER = "default_app_server";

    protected UnresolvedResourceException missingResourceException = null;
    protected int badReqCode = -1;
    private int phase = -1;
    // This boolean is set from CommCareSetupActivity -- If we are in keep
    // trying mode for installation, we want to sleep in between attempts to
    // launch this task
    private final boolean shouldSleep;

    // last time in system millis that we updated the status dialog
    private long lastTime = 0;

    protected String vAvailable;
    protected String vRequired;
    protected boolean majorIsProblem;

    public ResourceEngineTask(CommCareApp app, int taskId, boolean shouldSleep) {
        this.app = app;
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

            // --------------------------
            // Not upgrade mode, so attempting normal install
            global.setStateListener(this);
            platform.init(profileRef, global, false);
            // --------------------------

            // Initializes app resources and the app itself, including doing a
            // check to see if this app record was converted by the db upgrader
            CommCareApplication._().initializeGlobalResources(app);

            // Write this App Record to storage -- needs to be performed after
            // localizations have been initialized (by
            // initializeGlobalResources), so that getDisplayName() works
            app.writeInstalled();

            // update the current profile reference
            prefs = app.getAppPreferences();
            Editor edit = prefs.edit();
            if (platform.getCurrentProfile().getAuthReference() != null) {
                edit.putString(ResourceEngineTask.DEFAULT_APP_SERVER,
                        platform.getCurrentProfile().getAuthReference());
            } else {
                edit.putString(ResourceEngineTask.DEFAULT_APP_SERVER, profileRef);
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
