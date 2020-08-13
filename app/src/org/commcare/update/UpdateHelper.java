package org.commcare.update;

import android.content.Context;
import android.content.SharedPreferences;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AndroidResourceManager;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.engine.resource.installers.LocalStorageUnavailableException;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.preferences.PrefValues;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.InstallRequestSource;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResultAndError;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.PendingCalcs;
import org.commcare.views.dialogs.PinnedNotificationWithProgress;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.util.Vector;

import androidx.core.util.Pair;
import androidx.work.WorkManager;

import static org.commcare.CommCareApplication.areAutomatedActionsInvalid;

/**
 * Used to stage an update for the seated app in the background. Does not perform
 * actual update.
 *
 * Utilised By UpdateTask and UpdateWorker
 */
public class UpdateHelper implements TableStateListener {

    private static final String TAG = UpdateHelper.class.getSimpleName();
    private static UpdateHelper singletonRunningInstance = null;
    private static final Object lock = new Object();

    private final AndroidResourceManager mResourceManager;
    private final CommCareApp mApp;
    private UpdateProgressListener mUpdateProgressListener;
    private boolean isAutoUpdate;
    private int mAuthority;
    private PinnedNotificationWithProgress mPinnedNotificationProgress = null;
    private int mCurrentProgress = 0;
    private int mMaxProgress = 0;
    private static final String UPDATE_REQUEST_NAME = "update_request";

    private UpdateHelper(boolean autoUpdate, UpdateProgressListener updateProgressListener, InstallCancelled installCancelled) {
        mApp = CommCareApplication.instance().getCurrentApp();
        AndroidCommCarePlatform platform = mApp.getCommCarePlatform();
        mResourceManager = new AndroidResourceManager(platform);
        mResourceManager.setUpgradeListeners(this, installCancelled);
        isAutoUpdate = autoUpdate;
        mAuthority = Resource.RESOURCE_AUTHORITY_REMOTE;
        mUpdateProgressListener = updateProgressListener;
    }

    public static UpdateHelper getNewInstance(boolean autoUpdate, UpdateProgressListener updateProgressListener, InstallCancelled installCancelled) {
        synchronized (lock) {
            if (singletonRunningInstance == null) {
                singletonRunningInstance = new UpdateHelper(autoUpdate, updateProgressListener, installCancelled);
                return singletonRunningInstance;
            } else {
                throw new IllegalStateException("An instance of " + TAG + " already exists.");
            }
        }
    }

    // Main UpdateHelper function for staging updates
    public ResultAndError<AppInstallStatus> update(String profileRef, InstallRequestSource installRequestSource) {
        setupUpdate(profileRef);

        try {
            return new ResultAndError<>(stageUpdate(profileRef, installRequestSource));
        } catch (InvalidResourceException e) {
            ResourceInstallUtils.logInstallError(e,
                    "Structure error ocurred during install|");
            return new ResultAndError<>(AppInstallStatus.InvalidResource, buildCombinedErrorMessage(e.resourceName, e.getMessage()));
        } catch (LocalStorageUnavailableException e) {
            ResourceInstallUtils.logInstallError(e,
                    "Couldn't install file to local storage|");
            return new ResultAndError<>(AppInstallStatus.NoLocalStorage, e.getMessage());
        } catch (UnfullfilledRequirementsException e) {
            ResourceInstallUtils.logInstallError(e,
                    "App resources are incompatible with this device|");
            String error;
            if (e.isVersionMismatchException()) {
                error = Localization.get("update.version.mismatch", new String[]{e.getRequiredVersionString(), e.getAvailableVesionString()});
                error += " ";
                if (e.getRequirementType() == UnfullfilledRequirementsException.RequirementType.MAJOR_APP_VERSION) {
                    error += Localization.get("update.major.mismatch");
                } else if (e.getRequirementType() == UnfullfilledRequirementsException.RequirementType.MINOR_APP_VERSION) {
                    error += Localization.get("update.minor.mismatch");
                }
            } else {
                error = e.getMessage();
            }
            return new ResultAndError<>(AppInstallStatus.IncompatibleReqs, error);
        } catch (UnresolvedResourceException e) {
            return new ResultAndError<>(ResourceInstallUtils.processUnresolvedResource(e), e.getMessage());
        } catch (InstallCancelledException e) {
            return new ResultAndError<>(AppInstallStatus.Cancelled);
        } catch (Exception e) {
            ResourceInstallUtils.logInstallError(e,
                    "Unknown error ocurred during install|");
            return new ResultAndError<>(AppInstallStatus.UnknownFailure, e.getMessage());
        }
    }

    private void setupUpdate(String profileRef) {
        ResourceInstallUtils.recordUpdateAttemptTime(mApp);
        Logger.log(LogTypes.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRef);

        if (isAutoUpdate) {
            ResourceInstallUtils.recordAutoUpdateStart(mApp);
        }
    }


    private AppInstallStatus stageUpdate(String profileRef, InstallRequestSource installRequestSource)
            throws UnfullfilledRequirementsException, UnresolvedResourceException, InstallCancelledException {
        Resource profile = mResourceManager.getMasterProfile();
        boolean appInstalled = (profile != null &&
                profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED);

        if (!appInstalled) {
            return AppInstallStatus.UnknownFailure;
        }

        String profileRefWithParams =
                ResourceInstallUtils.addParamsToProfileReference(profileRef);

        return mResourceManager.checkAndPrepareUpgradeResources(profileRefWithParams, mAuthority, installRequestSource);
    }

    /**
     * Put both the resource in question and a detailed error message into one string for
     * ease of transport, which will be split out later and formatted into a user-readable
     * pinned notification
     */
    private static String buildCombinedErrorMessage(String head, String tail) {
        return "||" + head + "==" + tail;
    }


    // called on update completion
    public void OnUpdateComplete(ResultAndError<AppInstallStatus> resultAndError) {

        mResourceManager.recordStageUpdateResult(resultAndError);

        if (resultAndError.data.equals(AppInstallStatus.UpdateStaged)) {
            DataChangeLogger.log(new DataChangeLog.CommCareAppUpdateStaged());
        }

        if (!resultAndError.data.isUpdateInCompletedState()) {
            mResourceManager.processUpdateFailure(resultAndError.data);
        }

        if (mPinnedNotificationProgress != null) {
            mPinnedNotificationProgress.handleTaskCompletion(resultAndError);
        }

        if (isAutoUpdate) {
            ResourceInstallUtils.recordAutoUpdateCompletion(mApp);
        }
    }


    // Calculate and report the resource install progress a table has made.
    @Override
    public void compoundResourceAdded(ResourceTable table) {
        Vector<Resource> resources = AndroidResourceManager.getResourceListFromProfile(table);

        mCurrentProgress = 0;
        for (Resource r : resources) {
            int resourceStatus = r.getStatus();
            if (resourceStatus == Resource.RESOURCE_STATUS_UPGRADE ||
                    resourceStatus == Resource.RESOURCE_STATUS_INSTALLED) {
                mCurrentProgress += 1;
            }
        }
        mMaxProgress = resources.size();
        incrementProgress(mCurrentProgress, mMaxProgress);
    }

    @Override
    public void simpleResourceAdded() {
        incrementProgress(++mCurrentProgress, mMaxProgress);
    }

    @Override
    public void incrementProgress(int complete, int total) {
        mUpdateProgressListener.publishUpdateProgress(complete, total);
    }


    /**
     * Attaches pinned notification with a progress bar the task, which will
     * report updates to and close down the notification.
     *
     * @param ctx For launching notification and localizing text.
     */
    public void startPinnedNotification(Context ctx) {
        mPinnedNotificationProgress =
                new PinnedNotificationWithProgress(ctx, "updates.pinned.download",
                        "updates.pinned.progress", R.drawable.update_download_icon);
    }


    public void updateNotification(Integer... values) {
        if (mPinnedNotificationProgress != null) {
            mPinnedNotificationProgress.handleTaskUpdate(values);
        }
    }

    public void OnUpdateCancelled() {

        if (mPinnedNotificationProgress != null) {
            mPinnedNotificationProgress.handleTaskCancellation();
        }
        cancelUpgrade();
    }

    public void clearInstance() {
        synchronized (lock) {
            singletonRunningInstance = null;
        }
    }

    public void setLocalAuthority() {
        mAuthority = Resource.RESOURCE_AUTHORITY_LOCAL;
    }

    public void clearUpgrade() {
        mResourceManager.clearUpgrade();
    }


    private void cancelUpgrade() {
        mResourceManager.upgradeCancelled();
    }

    public int getCurrentProgress() {
        return mCurrentProgress;
    }

    public int getMaxProgress() {
        return mMaxProgress;
    }

    // Returns Unique request name for the UpdateWorker Request
    public static String getUpdateRequestName() {
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        return UPDATE_REQUEST_NAME + "_" + appId;
    }

    /**
     * @return True if we aren't a demo user and the time to check for an
     * update has elapsed or we logged out while an auto-update was downlaoding
     * or queued for retry.
     */
    public static boolean shouldAutoUpdate() {
        return (!areAutomatedActionsInvalid() &&
                isAutoUpdateOn());
    }

    // Returns true if update frequency is not set to never
    public static boolean isAutoUpdateOn() {
        return !getAutoUpdateFrequency().equals(PrefValues.FREQUENCY_NEVER);
    }

    // Returns the update frequency set in app settings
    private static String getAutoUpdateFrequency() {
        SharedPreferences preferences = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return preferences.getString(MainConfigurablePreferences.AUTO_UPDATE_FREQUENCY,
                PrefValues.FREQUENCY_NEVER);
    }

    /**
     * Returns periodicity for auto update worker in hours
     * the calculation for # of hours is based on the assumption that we want
     * to check thrice for an available update in the given time frame
     */
    public static int getAutoUpdatePeriodicity() {
        String autoUpdateFreq = getAutoUpdateFrequency();
        int checkEveryNHours;
        if (PrefValues.FREQUENCY_DAILY.equals(autoUpdateFreq)) {
            checkEveryNHours = 8; // 1/3th of a day
        } else {
            checkEveryNHours = 56; // 1/3th of a week
        }
        return checkEveryNHours;
    }

    // utility method to cancel the update worker
    public static void cancelUpdateWorker() {
        WorkManager.getInstance(CommCareApplication.instance())
                .cancelUniqueWork(getUpdateRequestName());
    }

    public static Pair<String, String> splitCombinedErrorMessage(String message) {
        String[] splitMessage = message.split("==", 2);
        return Pair.create(splitMessage[0].substring(2), splitMessage[1]);
    }

    public static boolean isCombinedErrorMessage(String message) {
        return message != null && message.startsWith("||");
    }

}
