package org.commcare.android.tasks;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.tasks.templates.ManagedAsyncTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.InstallAndUpdateUtils;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareResourceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.net.MalformedURLException;
import java.net.URL;
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
        implements TableStateListener {

    private static final String TAG = UpgradeTask.class.getSimpleName();

    private TaskListener<Integer, ResourceEngineOutcomes> taskListener = null;

    private static UpgradeTask singletonRunningInstance = null;

    private int currentProgress = 0;
    private int maxProgress = 0;

    public static final String KEY_START_OVER = "start_over_uprgrade";

    // 1 week in milliseconds
    private static final long START_OVER_THRESHOLD = 604800000;

    private UpgradeTask() {
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

        CommCareApp app = CommCareApplication._().getCurrentApp();
        AndroidCommCarePlatform platform = app.getCommCarePlatform();
        InstallAndUpdateUtils.recordUpdateAttempt(app.getAppPreferences());

        app.setupSandbox();

        Logger.log(AndroidLogger.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRef);

        try {
            // This is replicated in the application in a few places.
            ResourceTable global = platform.getGlobalResourceTable();

            // Ok, should figure out what the state of this bad boy is.
            Resource profile =
                global.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

            boolean appInstalled = (profile != null &&
                    profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED);

            if (!appInstalled) {
                return ResourceEngineOutcomes.StatusFailState;
            }
            global.setStateListener(this);

            // temporary is the upgrade table, which starts out in the
            // state that it was left after the last install- partially
            // populated if it stopped in middle, empty if the install was
            // successful
            ResourceTable upgradeTable = platform.getUpgradeResourceTable();
            ResourceTable recovery = platform.getRecoveryTable();
            upgradeTable.setStateListener(this);

            profileRef = addParamsToProfileReference(profileRef);

            CommCareResourceManager resourceManager = new CommCareResourceManager(platform);

            // instantiateLatestProfile
            //    - fetch into recovery
            //    - if newer than upgrade, clear upgrade, copy over recovery, download
            //    - if not newer, clear recovery
            // is table ready to install
            //   - no:
            //          start or resume downloading resources into update table
            //   - yes:
            //          return staged upadate flag
            // resourceManager.instantiateLatestProfile(upgradeTable, recovery);

            boolean startOverUpgrade = calcResourceFreshness();

            Resource upgradeProfileBeforeStage =
                    upgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

            try {
                // This populates the upgrade table with resources based on
                // binary files, starting with the profile file. If the new
                // profile is not a newer version, statgeUpgradeTable doesn't
                // actually pull in all the new references
                resourceManager.stageUpgradeTable(global, upgradeTable,
                        recovery, profileRef, startOverUpgrade);

                if (isTableStagedAndLatest(upgradeTable, upgradeProfileBeforeStage)) {
                    return ResourceEngineOutcomes.StatusUpdateStaged;
                }

                if (updateIsntNewer(upgradeTable, profile)) {
                    Logger.log(AndroidLogger.TYPE_RESOURCES, "App Resources up to Date");
                    upgradeTable.destroy();
                    return ResourceEngineOutcomes.StatusUpToDate;
                }

                resourceManager.prepareUpgradeResources(global, upgradeTable, recovery);
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

            System.out.println(upgradeTable.getTableReadiness());
            return ResourceEngineOutcomes.StatusUpdateStaged;
        } catch (Exception e) {
            InstallAndUpdateUtils.logInstallError(e,
                    "Unknown error ocurred during install|");
            return ResourceEngineOutcomes.StatusFailUnknown;
        }
    }

    private boolean isTableStagedAndLatest(ResourceTable upgradeTable,
                                           Resource upgradeProfileBeforeStage) {
        Resource newUpgradeProfile =
                upgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

        boolean versionNewerThanCurrentUpgradeTable = upgradeProfileBeforeStage == null || newUpgradeProfile.isNewer(upgradeProfileBeforeStage);
        System.out.print(upgradeTable.isReady());
        return (!versionNewerThanCurrentUpgradeTable &&
                upgradeTable.isReady());
    }

    private String addParamsToProfileReference(final String profileRef) {
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

    private boolean updateIsntNewer(ResourceTable upgradeTable,
                                    Resource currentProfile) {
        Resource newProfile =
            upgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);
        return newProfile != null && !newProfile.isNewer(currentProfile);
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

    public int getProgress() {
        return currentProgress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    @Override
    public void resourceStateUpdated(ResourceTable table) {
        Vector<Resource> resources = CommCareResourceManager.getResourceListFromProfile(table);

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

    /**
     * When set CommCarePlatform.stageUpgradeTable() will clear the last
     * version of the upgrade table and start over. Otherwise install reuses
     * the last version of the upgrade table.
     */
    public boolean calcResourceFreshness() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        /*
        long lastInstallTime = app.getAppPreferences().getLong(CommCareSetupActivity.KEY_LAST_INSTALL, -1);
        if (System.currentTimeMillis() - lastInstallTime > START_OVER_THRESHOLD) {
            // If we are triggering a start over install due to the time
            // threshold when there is a partial resource table that we could
            // be using, send a message to log this.
            ResourceTable temporary = app.getCommCarePlatform().getUpgradeResourceTable();
            if (temporary.getTableReadiness() == ResourceTable.RESOURCE_TABLE_PARTIAL) {
                Logger.log(AndroidLogger.TYPE_RESOURCES, "A start-over on installation has been "
                        + "triggered by the time threshold when there is an existing partial "
                        + "resource table that could be used.");
            }
            return true;
        } else {
            return app.getAppPreferences().getBoolean(KEY_START_OVER, true);
        }
        */
        return false;
    }
}
