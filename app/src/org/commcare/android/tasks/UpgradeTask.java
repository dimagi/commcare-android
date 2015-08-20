package org.commcare.android.tasks;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.tasks.templates.ManagedAsyncTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.InstallAndUpdateUtils;
import org.commcare.android.util.ResourceDownloadStats;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.ProcessCancelled;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCareResourceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.util.Vector;

/**
 * Upgrades the seated app in the background. If the user opens the Upgrade
 * activity, this task will report its progress to that activity. Enforces the
 * constraint that only one instance is ever running.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpgradeTask
        extends ManagedAsyncTask<String, Integer, ResourceEngineOutcomes>
        implements TableStateListener, ProcessCancelled {

    private static final String TAG = UpgradeTask.class.getSimpleName();

    private TaskListener<Integer, ResourceEngineOutcomes> taskListener = null;

    private static UpgradeTask singletonRunningInstance = null;

    private int currentProgress = 0;
    private int maxProgress = 0;

    private final CommCareResourceManager resourceManager;

    private UpgradeTask() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        AndroidCommCarePlatform platform = app.getCommCarePlatform();

        ResourceTable global = platform.getGlobalResourceTable();
        ResourceTable upgradeTable = platform.getUpgradeResourceTable();
        ResourceTable recovery = platform.getRecoveryTable();

        resourceManager =
                new CommCareResourceManager(platform, global, upgradeTable, recovery);
    }

    public static UpgradeTask getNewInstance() {
        if (singletonRunningInstance == null) {
            singletonRunningInstance = new UpgradeTask();
            return singletonRunningInstance;
        } else {
            throw new IllegalStateException("There is a " + TAG + " instance.");
        }
    }

    public static UpgradeTask getRunningInstance() {
        if (singletonRunningInstance != null &&
                singletonRunningInstance.getStatus() == Status.RUNNING) {
            return singletonRunningInstance;
        }
        return null;
    }

    @Override
    protected final ResourceEngineOutcomes doInBackground(String... params) {
        String profileRef = params[0];

        upgradeSetup(profileRef);

        try {
            return performUpgrade(profileRef);
        } catch (Exception e) {
            InstallAndUpdateUtils.logInstallError(e,
                    "Unknown error ocurred during install|");
            return ResourceEngineOutcomes.StatusFailUnknown;
        }
    }

    private void upgradeSetup(String profileRef) {
        InstallAndUpdateUtils.recordUpdateAttempt();

        CommCareApp app = CommCareApplication._().getCurrentApp();
        app.setupSandbox();

        Logger.log(AndroidLogger.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRef);
    }

    private ResourceEngineOutcomes performUpgrade(String profileRef) {
        Resource profile = resourceManager.getMasterProfile();

        boolean appInstalled = (profile != null &&
                profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED);

        if (!appInstalled) {
            return ResourceEngineOutcomes.StatusFailState;
        }

        profileRef =
                InstallAndUpdateUtils.addParamsToProfileReference(profileRef);

        // TODO PLM: detect if we are resuming a download and restore an
        // old download stat object
        ResourceDownloadStats resourceDownloadStats =
                new ResourceDownloadStats();
        resourceManager.setListeners(this, resourceDownloadStats);

        try {
            resourceManager.instantiateLatestProfile(profileRef);
            if (resourceManager.isUpgradeTableStaged()) {
                return ResourceEngineOutcomes.StatusUpdateStaged;
            }

            if (resourceManager.updateIsntNewer(profile)) {
                Logger.log(AndroidLogger.TYPE_RESOURCES, "App Resources up to Date");
                resourceManager.clearUpgradeTable();
                return ResourceEngineOutcomes.StatusUpToDate;
            }

            resourceManager.prepareUpgradeResources();
        } catch (InstallCancelledException e) {
            // The user cancelled the upgrade check process
            return ResourceEngineOutcomes.StatusFailUnknown;
        } catch (LocalStorageUnavailableException e) {
            InstallAndUpdateUtils.logInstallError(e,
                    "Couldn't install file to local storage|");
            return ResourceEngineOutcomes.StatusNoLocalStorage;
        } catch (UnfullfilledRequirementsException e) {
            if (e.isDuplicateException()) {
                return ResourceEngineOutcomes.StatusDuplicateApp;
            } else {
                InstallAndUpdateUtils.logInstallError(e,
                        "App resources are incompatible with this device|");
                return ResourceEngineOutcomes.StatusBadReqs;
            }
        } catch (UnresolvedResourceException e) {
            return InstallAndUpdateUtils.processUnresolvedResource(e);
        }

        return ResourceEngineOutcomes.StatusUpdateStaged;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);

        if (taskListener != null) {
            taskListener.processTaskUpdate(values);
        }
    }

    @Override
    protected void onPostExecute(ResourceEngineOutcomes result) {
        super.onPostExecute(result);

        boolean reusePartialTable =
                (result == ResourceEngineOutcomes.StatusFailState ||
                        result == ResourceEngineOutcomes.StatusNoLocalStorage);

        if (!reusePartialTable) {
            resourceManager.clearUpgradeTable();
        }

        if (taskListener != null) {
            taskListener.processTaskResult(result);
        }

        singletonRunningInstance = null;
    }

    @Override
    protected void onCancelled(ResourceEngineOutcomes result) {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            super.onCancelled(result);
        } else {
            super.onCancelled();
        }

        if (taskListener != null) {
            taskListener.processTaskCancel(result);
        }

        singletonRunningInstance = null;
    }

    public void registerTaskListener(TaskListener<Integer, ResourceEngineOutcomes> listener)
            throws TaskListenerException {
        if (taskListener != null) {
            throw new TaskListenerException("This " + TAG +
                    " was already registered with a TaskListener");
        }
        taskListener = listener;
    }

    public void unregisterTaskListener(TaskListener<Integer, ResourceEngineOutcomes> listener)
            throws TaskListenerException {
        if (listener != taskListener) {
            throw new TaskListenerException("The provided listener wasn't " +
                    "registered with this " + TAG);
        }
        taskListener = null;
    }

    @Override
    public void resourceStateUpdated(ResourceTable table) {
        Vector<Resource> resources = CommCareResourceManager.getResourceListFromProfile(table);

        currentProgress = 0;
        for (Resource r : resources) {
            switch (r.getStatus()) {
                case Resource.RESOURCE_STATUS_UPGRADE:
                    currentProgress += 1;
                    break;
                case Resource.RESOURCE_STATUS_INSTALLED:
                    currentProgress += 1;
                    break;
            }
        }
        maxProgress = resources.size();
        incrementProgress(currentProgress, maxProgress);
    }

    @Override
    public void incrementProgress(int complete, int total) {
        this.publishProgress(complete, total);
    }

    @Override
    public boolean processWasCancelled() {
        return isCancelled();
    }

    public int getProgress() {
        return currentProgress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }
}
