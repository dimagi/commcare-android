package org.commcare.update;

import android.content.Context;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AndroidResourceManager;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.engine.resource.installers.LocalStorageUnavailableException;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.UpdateTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.PendingCalcs;
import org.commcare.views.dialogs.PinnedNotificationWithProgress;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.util.Vector;

import androidx.core.util.Pair;

import static org.commcare.CommCareApplication.areAutomatedActionsInvalid;

public class UpdateHelper implements TableStateListener {

    private final AndroidResourceManager mResourceManager;
    private final CommCareApp mApp;
    private final Context mContext;
    private final UpdateProgressListener mUpdateProgressListener;
    private boolean isAutoUpdate;
    private int mAuthority;
    private PinnedNotificationWithProgress mPinnedNotificationProgress = null;
    private int mCurrentProgress = 0;
    private int mMaxProgress = 0;
    private boolean isUpdateCancelledByUser = false;
    private static final String UPDATE_REQUEST_NAME = "update_request";

    public UpdateHelper(boolean autoUpdate, UpdateProgressListener updateProgressListener, InstallCancelled installCancelled) {

        mApp = CommCareApplication.instance().getCurrentApp();
        AndroidCommCarePlatform platform = mApp.getCommCarePlatform();
        mResourceManager = new AndroidResourceManager(platform);
        mResourceManager.setUpgradeListeners(this, installCancelled);
        isAutoUpdate = autoUpdate;
        mAuthority = Resource.RESOURCE_AUTHORITY_REMOTE;
        mContext = CommCareApplication.instance();
        mUpdateProgressListener = updateProgressListener;
    }

    public ResultAndError<AppInstallStatus> update(String profileRef) {
        setupUpdate(isAutoUpdate, profileRef);

        try {
            return new ResultAndError<>(stageUpdate(profileRef));
        } catch (InvalidResourceException e) {
            ResourceInstallUtils.logInstallError(e,
                    "Structure error ocurred during install|");
            return new ResultAndError<>(AppInstallStatus.UnknownFailure, buildCombinedErrorMessage(e.resourceName, e.getMessage()));
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
            // onPostExecute doesn't get invoked in case of task cancellation, so process the failure here
            mResourceManager.processUpdateFailure(AppInstallStatus.UnknownFailure, mContext, isAutoUpdate);
            return new ResultAndError<>(AppInstallStatus.UnknownFailure);
        } catch (Exception e) {
            ResourceInstallUtils.logInstallError(e,
                    "Unknown error ocurred during install|");
            return new ResultAndError<>(AppInstallStatus.UnknownFailure, e.getMessage());
        }
    }

    private void setupUpdate(boolean autoUpdate, String profileRef) {
        ResourceInstallUtils.recordUpdateAttemptTime(mApp);

        if (autoUpdate) {
            ResourceInstallUtils.recordAutoUpdateStart(mApp);
        }

        mResourceManager.incrementUpdateAttempts();

        Logger.log(LogTypes.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRef);
    }


    private AppInstallStatus stageUpdate(String profileRef) throws UnfullfilledRequirementsException,
            UnresolvedResourceException, InstallCancelledException {
        Resource profile = mResourceManager.getMasterProfile();
        boolean appInstalled = (profile != null &&
                profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED);

        if (!appInstalled) {
            return AppInstallStatus.UnknownFailure;
        }

        String profileRefWithParams =
                ResourceInstallUtils.addParamsToProfileReference(profileRef);

        return mResourceManager.checkAndPrepareUpgradeResources(profileRefWithParams, mAuthority);
    }

    /**
     * Put both the resource in question and a detailed error message into one string for
     * ease of transport, which will be split out later and formatted into a user-readable
     * pinned notification
     */
    private static String buildCombinedErrorMessage(String head, String tail) {
        return "||" + head + "==" + tail;
    }

    public void setLocalAuthority() {
        mAuthority = Resource.RESOURCE_AUTHORITY_LOCAL;
    }

    public void clearUpgrade() {
        mResourceManager.clearUpgrade();
    }

    public void OnUpdateComplete(ResultAndError<AppInstallStatus> resultAndError) {
        if (resultAndError.data.equals(AppInstallStatus.UpdateStaged)) {
            DataChangeLogger.log(new DataChangeLog.CommCareAppUpdateStaged());
        }

        if (!resultAndError.data.isUpdateInCompletedState()) {
            mResourceManager.processUpdateFailure(resultAndError.data, mContext, isAutoUpdate);
        } else if (isAutoUpdate) {
            // auto-update was successful or app was up-to-date.
            ResourceInstallUtils.recordAutoUpdateCompletion(mApp);
        }

        if (mPinnedNotificationProgress != null) {
            mPinnedNotificationProgress.handleTaskCompletion(resultAndError);
        }
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


    public static Pair<String, String> splitCombinedErrorMessage(String message) {
        String[] splitMessage = message.split("==", 2);
        return Pair.create(splitMessage[0].substring(2), splitMessage[1]);
    }

    public static boolean isCombinedErrorMessage(String message) {
        return message != null && message.startsWith("||");
    }


    public void updateNotification(Integer... values) {
        if (mPinnedNotificationProgress != null) {
            mPinnedNotificationProgress.handleTaskUpdate(values);
        }
    }

    public void OnUpdateCancelled() {
        if (isUpdateCancelledByUser && isAutoUpdate) {
            // task may have been cancelled by logout, in which case we want
            // to keep trying to auto-update upon logging in again.
            ResourceInstallUtils.recordAutoUpdateCompletion(mApp);
        }
        isUpdateCancelledByUser = false;

        if (mPinnedNotificationProgress != null) {
            mPinnedNotificationProgress.handleTaskCancellation();
        }
        cancelUpgrade();
    }

    /**
     * Calculate and report the resource install progress a table has made.
     */
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


    private void cancelUpgrade() {
        mResourceManager.upgradeCancelled();
    }

    public int getCurrentProgress() {
        return mCurrentProgress;
    }

    public int getMaxProgress() {
        return mMaxProgress;
    }

    public void setUpdateCancelledByUser(boolean updateCancelledByUser) {
        isUpdateCancelledByUser = updateCancelledByUser;
    }

    public static String getUpdateRequestName(String appId) {
        return UPDATE_REQUEST_NAME + "_" + appId;
    }

    /**
     * @return True if we aren't a demo user and the time to check for an
     * update has elapsed or we logged out while an auto-update was downlaoding
     * or queued for retry.
     */
    public static boolean shouldAutoUpdate() {
        return true;
//        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
//
//        return (!areAutomatedActionsInvalid() &&
//                (ResourceInstallUtils.shouldAutoUpdateResume(currentApp) ||
//                        PendingCalcs.isUpdatePending(currentApp.getAppPreferences())));
    }
}
