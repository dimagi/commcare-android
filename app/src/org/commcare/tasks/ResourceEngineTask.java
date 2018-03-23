package org.commcare.tasks;

import android.os.SystemClock;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.engine.resource.installers.LocalStorageUnavailableException;
import org.commcare.resources.ResourceManager;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
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

    private int installedResourceCountWhileUpdating = 0;
    private int installedResourceCount = 0;
    private int totalResourceCount = -1;

    private UnresolvedResourceException missingResourceException = null;
    private InvalidResourceException invalidResourceException = null;
    private int phase = -1;
    // This boolean is set from CommCareSetupActivity -- If we are in keep
    // trying mode for installation, we want to sleep in between attempts to
    // launch this task
    private final boolean shouldSleep;

    private String versionAvailable;
    private String versionRequired;
    private boolean majorIsProblem;

    private final Object statusLock = new Object();
    private boolean statusCheckRunning = false;

    private int authorityForInstall;

    public ResourceEngineTask(CommCareApp app, int taskId, boolean shouldSleep, int authority) {
        this.app = app;
        this.taskId = taskId;
        this.shouldSleep = shouldSleep;
        this.authorityForInstall = authority;

        TAG = ResourceEngineTask.class.getSimpleName();
    }

    @Override
    protected AppInstallStatus doTaskBackground(String... profileRefs) {
        String profileRef = profileRefs[0];
        ResourceInstallUtils.recordUpdateAttemptTime(app);

        app.setupSandbox();

        Logger.log(LogTypes.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRefs[0]);

        if (shouldSleep) {
            SystemClock.sleep(2000);
        }

        try {
            AndroidCommCarePlatform platform = app.getCommCarePlatform();
            ResourceTable global = platform.getGlobalResourceTable();

            global.setStateListener(this);
            try {
                ResourceManager.installAppResources(platform, profileRef, global, false, authorityForInstall);
            } catch (LocalStorageUnavailableException e) {
                ResourceInstallUtils.logInstallError(e,
                        "Couldn't install file to local storage|");
                return AppInstallStatus.NoLocalStorage;
            } catch (UnfullfilledRequirementsException e) {
                if (e.isDuplicateException()) {
                    return AppInstallStatus.DuplicateApp;
                } else if (e.isIncorrectTargetException()) {
                    switch (CommCareApplication.instance().getPackageName()) {
                        case "org.commcare.lts":
                            return AppInstallStatus.IncorrectTargetPackageLTS;
                        default:
                            return AppInstallStatus.IncorrectTargetPackage;
                    }
                } else {
                    versionAvailable = e.getAvailableVesionString();
                    versionRequired = e.getRequiredVersionString();
                    majorIsProblem = e.getRequirementType() == UnfullfilledRequirementsException.RequirementType.DUPLICATE_APP;

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
            } catch (InvalidResourceException e) {
                invalidResourceException = e;
                return AppInstallStatus.InvalidResource;
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
    public void compoundResourceAdded(final ResourceTable table) {
        synchronized (statusLock) {
            // if last time isn't set or is less than our spacing count, do not
            // perform status update. Also if we are already running one, just skip this.
            if (statusCheckRunning) {
                return;
            }

            //Otherwise fire off a new check

            Runnable statusUpdateCheck = new Runnable() {
                @Override
                public void run() {
                    Vector<Resource> resources;
                    try {
                        resources = ResourceManager.getResourceListFromProfile(table);
                    } catch (Exception e) {
                        // Since we're on a seperate thread, the db can close during the process
                        // before we can catch the cancel when the install finishes. If so, 
                        // we can skip the status check entirely.
                        signalStatusCheckComplete();
                        return;
                    }

                    installedResourceCount = 0;
                    totalResourceCount = resources.size();
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
                                installedResourceCount++;
                                break;
                            case Resource.RESOURCE_STATUS_INSTALLED:
                                installedResourceCount++;
                                break;
                        }
                    }
                    if (!forceClosed) {
                        incrementProgress(installedResourceCount, totalResourceCount);
                    }
                    signalStatusCheckComplete();
                }

                private void signalStatusCheckComplete() {
                    synchronized (statusLock) {
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
    public void simpleResourceAdded() {
        if (statusCheckRunning) {
            installedResourceCountWhileUpdating++;
        } else {
            installedResourceCount += installedResourceCountWhileUpdating + 1;
            installedResourceCountWhileUpdating = 0;
            incrementProgress(installedResourceCount, totalResourceCount);
        }
    }

    @Override
    public void incrementProgress(int complete, int total) {
        this.publishProgress(new int[]{complete, total, phase});
    }

    public UnresolvedResourceException getMissingResourceException() {
        return missingResourceException;
    }

    public InvalidResourceException getInvalidResourceException() {
        return invalidResourceException;
    }

    public String getVersionAvailable() {
        return versionAvailable;
    }

    public String getVersionRequired() {
        return versionRequired;
    }

    public boolean isMajorIsProblem() {
        return majorIsProblem;
    }
}
