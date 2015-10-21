package org.commcare.android.tasks;

import android.content.Context;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.resource.AndroidResourceManager;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.tasks.templates.ManagedAsyncTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.PinnedNotificationWithProgress;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.javarosa.core.services.Logger;

import java.util.Vector;

/**
 * Stages an update for the seated app in the background. Does not perform
 * actual update. If the user opens the Update activity, this task will report
 * its progress to that activity.  Enforces the constraint that only one
 * instance is ever running.
 *
 * Will be cancelled on user logout, but can still run if no user is logged in.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateTask
        extends ManagedAsyncTask<String, Integer, AppInstallStatus>
        implements TableStateListener, InstallCancelled {

    private static final String TAG = UpdateTask.class.getSimpleName();
    private static final Object lock = new Object();

    private static UpdateTask singletonRunningInstance = null;

    private final AndroidResourceManager resourceManager;
    private final CommCareApp app;

    private TaskListener<Integer, AppInstallStatus> taskListener = null;
    private PinnedNotificationWithProgress pinnedNotificationProgress = null;
    private Context ctx;
    private String profileRef;
    private boolean wasTriggeredByAutoUpdate = false;
    private boolean taskWasCancelledByUser = false;
    private int currentProgress = 0;
    private int maxProgress = 0;

    private UpdateTask() {
        app = CommCareApplication._().getCurrentApp();
        AndroidCommCarePlatform platform = app.getCommCarePlatform();

        resourceManager =
                new AndroidResourceManager(platform);

        resourceManager.setUpgradeListeners(this, this);
    }

    public static UpdateTask getNewInstance() {
        synchronized (lock) {
            if (singletonRunningInstance == null) {
                singletonRunningInstance = new UpdateTask();
                return singletonRunningInstance;
            } else {
                throw new IllegalStateException("An instance of " + TAG + " already exists.");
            }
        }
    }

    public static UpdateTask getRunningInstance() {
        synchronized (lock) {
            if (singletonRunningInstance != null &&
                    singletonRunningInstance.getStatus() == Status.RUNNING) {
                return singletonRunningInstance;
            }
            return null;
        }
    }

    /**
     * Attaches pinned notification with a progress bar the task, which will
     * report updates to and close down the notification.
     *
     * @param ctx For launching notification and localizing text.
     */
    public void startPinnedNotification(Context ctx) {
        this.ctx = ctx;

        pinnedNotificationProgress =
                new PinnedNotificationWithProgress(ctx, "updates.pinned.download",
                        "updates.pinned.progress", R.drawable.update_download_icon);

    }

    @Override
    protected final AppInstallStatus doInBackground(String... params) {
        profileRef = params[0];

        setupUpdate();

        try {
            return stageUpdate();
        } catch (Exception e) {
            ResourceInstallUtils.logInstallError(e,
                    "Unknown error ocurred during install|");
            return AppInstallStatus.UnknownFailure;
        }
    }

    private void setupUpdate() {
        ResourceInstallUtils.recordUpdateAttemptTime(app);

        if (wasTriggeredByAutoUpdate) {
            ResourceInstallUtils.recordAutoUpdateStart(app);
        }

        resourceManager.incrementUpdateAttempts();

        app.setupSandbox();

        Logger.log(AndroidLogger.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRef);
    }

    private AppInstallStatus stageUpdate() {
        Resource profile = resourceManager.getMasterProfile();
        boolean appInstalled = (profile != null &&
                profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED);

        if (!appInstalled) {
            return AppInstallStatus.UnknownFailure;
        }

        String profileRefWithParams =
                ResourceInstallUtils.addParamsToProfileReference(profileRef);

        return resourceManager.checkAndPrepareUpgradeResources(profileRefWithParams);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);

        if (pinnedNotificationProgress != null) {
            pinnedNotificationProgress.handleTaskUpdate(values);
        }
        if (taskListener != null) {
            taskListener.handleTaskUpdate(values);
        }
    }

    @Override
    protected void onPostExecute(AppInstallStatus result) {
        super.onPostExecute(result);

        if (!result.isUpdateInCompletedState()) {
            resourceManager.processUpdateFailure(result, ctx, wasTriggeredByAutoUpdate);
        } else if (wasTriggeredByAutoUpdate) {
            // auto-update was successful or app was up-to-date.
            ResourceInstallUtils.recordAutoUpdateCompletion(app);
        }

        if (pinnedNotificationProgress != null) {
            pinnedNotificationProgress.handleTaskCompletion(result);
        }
        if (taskListener != null) {
            taskListener.handleTaskCompletion(result);
        }

        clearTaskInstance();
    }

    public static void clearTaskInstance() {
        synchronized (lock) {
            singletonRunningInstance = null;
        }
    }

    @Override
    protected void onCancelled(AppInstallStatus result) {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            super.onCancelled(result);
        } else {
            super.onCancelled();
        }

        if (taskWasCancelledByUser && wasTriggeredByAutoUpdate) {
            // task may have been cancelled by logout, in which case we want
            // to keep trying to auto-update upon logging in again.
            ResourceInstallUtils.recordAutoUpdateCompletion(app);
        }
        taskWasCancelledByUser = false;

        if (pinnedNotificationProgress != null) {
            pinnedNotificationProgress.handleTaskCancellation(result);
        }

        if (taskListener != null) {
            taskListener.handleTaskCancellation(result);
        }

        resourceManager.upgradeCancelled();

        clearTaskInstance();
    }

    /**
     * Start reporting progress with a listener process.
     *
     * @throws TaskListenerRegistrationException If this task was already
     *                                           registered with a listener
     */
    public void registerTaskListener(TaskListener<Integer, AppInstallStatus> listener)
            throws TaskListenerRegistrationException {
        if (taskListener != null) {
            throw new TaskListenerRegistrationException("This " + TAG +
                    " was already registered with a TaskListener");
        }
        taskListener = listener;
    }

    /**
     * Stop reporting progress with a listener process
     *
     * @throws TaskListenerRegistrationException If this task wasn't registered
     *                                           with the unregistering listener.
     */
    public void unregisterTaskListener(TaskListener<Integer, AppInstallStatus> listener)
            throws TaskListenerRegistrationException {
        if (listener != taskListener) {
            throw new TaskListenerRegistrationException("The provided listener wasn't " +
                    "registered with this " + TAG);
        }
        taskListener = null;
    }

    /**
     * Calculate and report the resource install progress a table has made.
     */
    @Override
    public void resourceStateUpdated(ResourceTable table) {
        Vector<Resource> resources =
                AndroidResourceManager.getResourceListFromProfile(table);

        currentProgress = 0;
        for (Resource r : resources) {
            int resourceStatus = r.getStatus();
            if (resourceStatus == Resource.RESOURCE_STATUS_UPGRADE ||
                    resourceStatus == Resource.RESOURCE_STATUS_INSTALLED) {
                currentProgress += 1;
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
     * Allows resource installation process to check if this task was cancelled
     */
    @Override
    public boolean wasInstallCancelled() {
        return isCancelled();
    }

    public int getProgress() {
        return currentProgress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    /**
     * Register task as triggered by auto update; used to determine retry behaviour.
     */
    public void setAsAutoUpdate() {
        wasTriggeredByAutoUpdate = true;
    }

    /**
     * Record that task cancellation was triggered by user, not the app logging
     * out. Useful for knowing if an auto-update should resume or not upon next
     * login.
     */
    public void cancelWasUserTriggered() {
        taskWasCancelledByUser = true;
    }
}
