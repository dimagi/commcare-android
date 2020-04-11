package org.commcare.appupdate;

import android.app.Activity;
import android.content.IntentSender;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.install.InstallState;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

import org.javarosa.core.services.Logger;

/**
 * @author $|-|!˅@M
 */
public class CommcareFlexibleAppUpdateManager implements FlexibleAppUpdateController {

    public static final int IN_APP_UPDATE_REQUEST_CODE = 1212;
    private static final String TAG = CommcareFlexibleAppUpdateManager.class.getName();

    private final AppUpdateManager mAppUpdateManager;
    private AppUpdateInfo mAppUpdateInfo;
    private final Runnable mCallback;
    private boolean mEnabled;

    private @UpdateAvailability int mUpdateAvailability;
    private @InstallStatus int mInstallStatus;
    private AppUpdateState mAppUpdateState;

    /**
     * callback {@link Runnable} to notify when update state changes.
     */
    CommcareFlexibleAppUpdateManager(Runnable callback, AppUpdateManager appUpdateManager) {
        this.mCallback = callback;
        this.mAppUpdateManager = appUpdateManager;
        mAppUpdateState = null;
    }

    @Override
    public void register() {
        mEnabled = true;
        mAppUpdateManager.registerListener(this);
        fetchAppUpdateInfo();
    }

    @Override
    public void unregister() {
        mEnabled = false;
        mAppUpdateManager.unregisterListener(this);
    }

    @Override
    public void startUpdate(Activity activity) {
        try {
            boolean success = mAppUpdateManager.startUpdateFlowForResult(
                    mAppUpdateInfo, AppUpdateType.FLEXIBLE, activity, IN_APP_UPDATE_REQUEST_CODE);
            if (!success) {
                Logger.log(TAG, "startUpdate|requested update cannot be started");
            }
        } catch (IntentSender.SendIntentException exception) {
            mInstallStatus = InstallStatus.FAILED;
            Logger.log(TAG, "startUpdate|failed with : " + exception.getMessage());
        }
    }

    @Override
    public AppUpdateState getStatus() {
        return mAppUpdateState;
    }

    @Override
    public void completeUpdate() {
        mAppUpdateManager.completeUpdate()
                .addOnSuccessListener(result -> {
                    Logger.log(TAG, "completeUpdate|was successful");
                    publishStatus();
                })
                .addOnFailureListener(exception -> {
                    Logger.log(TAG, "completeUpdate|failed with : " + exception.getMessage());
                    publishStatus();
                });
    }

    @Override
    public void onStateUpdate(InstallState state) {
        Logger.log(TAG, "Install state updated with install status: " + state.installStatus()
                + ", and error code: " + state.installErrorCode());

        if (mInstallStatus != state.installStatus()) {
            mInstallStatus = state.installStatus();
            publishStatus();
        }
    }

    //region Private helpers
    private void fetchAppUpdateInfo() {
        mAppUpdateManager.getAppUpdateInfo()
                .addOnSuccessListener(info -> {
                    mAppUpdateInfo = info;
                    mUpdateAvailability = info.updateAvailability();
                    mInstallStatus = info.installStatus();
                    Logger.log(TAG, "fetchAppUpdateInfo|success with UpdateAvailability : "
                            + mUpdateAvailability + ", and InstallStatus : " + mInstallStatus);
                    publishStatus();
                })
                .addOnFailureListener(exception -> {
                    mAppUpdateInfo = null;
                    mUpdateAvailability = UpdateAvailability.UNKNOWN;
                    mInstallStatus = InstallStatus.UNKNOWN;
                    Logger.log(TAG, "fetchAppUpdateInfo|failed with : " + exception.getMessage());
                    publishStatus();
                });
    }

    /**
     * Pings the listener using {@link #mCallback} when there is an update regarding installation state.
     */
    private void publishStatus() {
        if (!mEnabled) {
            return;
        }
        AppUpdateState newState = getAppUpdateState();
        if (mAppUpdateState == newState) {
            return;
        }
        Logger.log(TAG, "Publishing status update to : " + newState.name() + ", from : "
                + (mAppUpdateState != null ? mAppUpdateState.name() : "null"));
        mAppUpdateState = newState;
        mCallback.run();
    }

    /**
     * Gets {@link AppUpdateState} using {@link #mUpdateAvailability} and {@link #mInstallStatus}
     */
    private AppUpdateState getAppUpdateState() {
        AppUpdateState state = AppUpdateState.UNAVAILABLE;
        switch (mInstallStatus) {
            case InstallStatus.PENDING:
            case InstallStatus.DOWNLOADING:
                state = AppUpdateState.DOWNLOADING;
                break;
            case InstallStatus.DOWNLOADED:
                state = AppUpdateState.DOWNLOADED;
                break;
            case InstallStatus.FAILED:
                state = AppUpdateState.FAILED;
                break;
        }
        if (state == AppUpdateState.UNAVAILABLE && mUpdateAvailability == UpdateAvailability.UPDATE_AVAILABLE) {
            state = AppUpdateState.AVAILABLE;
        }
        return state;
    }
    //endregion
}
