package org.commcare.android.tasks;

import android.os.SystemClock;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.InstallAndUpdateUtils;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.xml.CommCareElementParser;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.util.Vector;

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
        InstallAndUpdateUtils.recordUpdateAttempt(app.getAppPreferences());

        app.setupSandbox();

        Logger.log(AndroidLogger.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRefs[0]);

        if (shouldSleep) {
            SystemClock.sleep(2000);
        }

        try {
            // This is replicated in the application in a few places.
            ResourceTable global = platform.getGlobalResourceTable();

            // Not upgrade mode, so attempting normal install
            global.setStateListener(this);
            try {
                platform.init(profileRef, global, false);
            } catch (LocalStorageUnavailableException e) {
                InstallAndUpdateUtils.logInstallError(e,
                        "Couldn't install file to local storage|");
                return ResourceEngineOutcomes.StatusNoLocalStorage;
            } catch (UnfullfilledRequirementsException e) {
                if (e.isDuplicateException()) {
                    return ResourceEngineOutcomes.StatusDuplicateApp;
                } else {
                    badReqCode = e.getRequirementCode();
                    vAvailable = e.getAvailableVesionString();
                    vRequired = e.getRequiredVersionString();
                    majorIsProblem = e.getRequirementCode() == CommCareElementParser.REQUIREMENT_MAJOR_APP_VERSION;

                    InstallAndUpdateUtils.logInstallError(e,
                            "App resources are incompatible with this device|");
                    return ResourceEngineOutcomes.StatusBadReqs;
                }
            } catch (UnresolvedResourceException e) {
                ResourceEngineOutcomes outcome =
                    InstallAndUpdateUtils.processUnresolvedResource(e);
                if (outcome != ResourceEngineOutcomes.StatusBadCertificate) {
                    missingResourceException = e;
                }
                return outcome;
            }

            InstallAndUpdateUtils.initAndCommitApp(app,
                    profileRef,
                    platform.getCurrentProfile().getAuthReference());

            return ResourceEngineOutcomes.StatusInstalled;
        } catch (Exception e) {
            InstallAndUpdateUtils.logInstallError(e,
                    "Unknown error ocurred during install|");
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
