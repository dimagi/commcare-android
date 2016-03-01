package org.commcare.android.tasks;

import android.os.SystemClock;

import org.commcare.android.logging.AndroidLogger;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.resources.ResourceManager;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.xml.CommCareElementParser;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.util.Vector;

/**
 * @author ctsims
 */
public abstract class ResourceEngineTask<R>
        extends CommCareTask<String, int[], AppInstallStatus, R>
        implements TableStateListener {

    private final CommCareApp app;

    private static final int PHASE_CHECKING = 0;
    public static final int PHASE_DOWNLOAD = 1;

    /**
     * Wait time between dialog updates in milliseconds
     */
    private static final long STATUS_UPDATE_WAIT_TIME = 1000;

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

    private final Object statusLock = new Object();
    private boolean statusCheckRunning = false;

    public ResourceEngineTask(CommCareApp app, int taskId, boolean shouldSleep) {
        this.app = app;
        this.taskId = taskId;
        this.shouldSleep = shouldSleep;

        TAG = ResourceEngineTask.class.getSimpleName();
    }

    @Override
    protected AppInstallStatus doTaskBackground(String... profileRefs) {
        String profileRef = profileRefs[0];
        ResourceInstallUtils.recordUpdateAttemptTime(app);

        app.setupSandbox();

        Logger.log(AndroidLogger.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRefs[0]);

        if (shouldSleep) {
            SystemClock.sleep(2000);
        }

        try {
            AndroidCommCarePlatform platform = app.getCommCarePlatform();
            ResourceTable global = platform.getGlobalResourceTable();

            global.setStateListener(this);
            try {
                ResourceManager.installAppResources(platform, profileRef, global, false);
            } catch (LocalStorageUnavailableException e) {
                ResourceInstallUtils.logInstallError(e,
                        "Couldn't install file to local storage|");
                return AppInstallStatus.NoLocalStorage;
            } catch (UnfullfilledRequirementsException e) {
                if (e.isDuplicateException()) {
                    return AppInstallStatus.DuplicateApp;
                } else {
                    badReqCode = e.getRequirementCode();
                    vAvailable = e.getAvailableVesionString();
                    vRequired = e.getRequiredVersionString();
                    majorIsProblem = e.getRequirementCode() == CommCareElementParser.REQUIREMENT_MAJOR_APP_VERSION;

                    ResourceInstallUtils.logInstallError(e,
                            "App resources are incompatible with this device|");
                    return AppInstallStatus.IncompatibleReqs;
                }
            } catch (UnresolvedResourceException e) {
                AppInstallStatus outcome =
                        ResourceInstallUtils.processUnresolvedResource(e);
                if (outcome != AppInstallStatus.BadCertificate) {
                    missingResourceException = e;
                }
                return outcome;
            }

            ResourceInstallUtils.initAndCommitApp(app, profileRef);

            return AppInstallStatus.Installed;
        } catch (Exception e) {
            e.printStackTrace();
            ResourceInstallUtils.logInstallError(e,
                    "Unknown error ocurred during install|");
            return AppInstallStatus.UnknownFailure;
        }
    }

    @Override
    public void resourceStateUpdated(final ResourceTable table) {
        synchronized (statusLock) {
            // if last time isn't set or is less than our spacing count, do not
            // perform status update. Also if we are already running one, just skip this.
            if (statusCheckRunning || System.currentTimeMillis() - lastTime < ResourceEngineTask.STATUS_UPDATE_WAIT_TIME) {
                return;
            }

            //Otherwise fire off a new check

            Runnable statusUpdateCheck = new Runnable() {
                @Override
                public void run() {
                    Vector<Resource> resources;
                    try {
                        resources = ResourceManager.getResourceListFromProfile(table);
                    } catch(IllegalStateException e) {
                        // Since we're on a seperate thread, the db can close during the process
                        // before we can catch the cancel when the install finishes. If so, 
                        // we can skip the status check entirely.
                        signalStatusCheckComplete();
                        return;
                    }

                    int score = 0;
                    boolean forceClosed = false;
                    for (Resource r : resources) {
                        forceClosed = ResourceEngineTask.this.getStatus() == Status.FINISHED ||
                                ResourceEngineTask.this.isCancelled();
                        if (forceClosed) {
                            break;
                        }
                        switch (r.getStatus()) {
                            case Resource.RESOURCE_STATUS_UPGRADE:
                                // If we spot an upgrade after we've started the upgrade process,
                                // something now needs to be updated
                                if (phase == PHASE_CHECKING) {
                                    ResourceEngineTask.this.phase = PHASE_DOWNLOAD;
                                }
                                score += 1;
                                break;
                            case Resource.RESOURCE_STATUS_INSTALLED:
                                score += 1;
                                break;
                        }
                    }
                    if (!forceClosed) {
                        incrementProgress(score, resources.size());
                    }
                    signalStatusCheckComplete();
                }
                
                private void signalStatusCheckComplete() {
                    synchronized (statusLock) {
                        lastTime = System.currentTimeMillis();
                        statusCheckRunning = false;
                    }

                }
            };
            statusCheckRunning = true;
            Thread t = new Thread(statusUpdateCheck);
            t.start();
        }
    }

    @Override
    public void incrementProgress(int complete, int total) {
        this.publishProgress(new int[]{complete, total, phase});
    }
}
