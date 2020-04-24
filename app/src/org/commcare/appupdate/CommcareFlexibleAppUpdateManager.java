package org.commcare.appupdate;

import android.app.Activity;
import android.content.IntentSender;

import androidx.annotation.NonNull;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.install.InstallState;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallErrorCode;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import org.javarosa.core.services.Logger;

import javax.annotation.Nullable;

/**
 * @author $|-|!˅@M
 */
public class CommcareFlexibleAppUpdateManager implements FlexibleAppUpdateController {

    private static final String TAG = CommcareFlexibleAppUpdateManager.class.getName();

    private final AppUpdateManager mAppUpdateManager;
    private @Nullable AppUpdateInfo mAppUpdateInfo;
    private final Runnable mCallback;
    private boolean mEnabled;

    private @UpdateAvailability int mUpdateAvailability;
    private @InstallStatus int mInstallStatus;
    private AppUpdateState mAppUpdateState;

    private int percentageDownloaded;
    private @InstallErrorCode int mInstallErrorCode = InstallErrorCode.NO_ERROR;

    /**
     * callback {@link Runnable} to notify when update state changes.
     */
    CommcareFlexibleAppUpdateManager(Runnable callback, AppUpdateManager appUpdateManager) {
        this.mCallback = callback;
        this.mAppUpdateManager = appUpdateManager;
        mAppUpdateState = null;
    }

    //region AppUpdateController implementation
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
            mInstallErrorCode = InstallErrorCode.ERROR_INTERNAL_ERROR;
            Logger.log(TAG, "startUpdate|failed with : " + exception.getMessage());
            publishStatus();
        }
    }

    @NonNull
    @Override
    public AppUpdateState getStatus() {
        return mAppUpdateState;
    }

    @Override
    public void completeUpdate() {
        mAppUpdateManager.completeUpdate()
                .addOnSuccessListener(result -> {
                    Logger.log(TAG, "completeUpdate|was successful");
                })
                .addOnFailureListener(exception -> {
                    Logger.log(TAG, "completeUpdate|failed with : " + exception.getMessage());
                    publishStatus();
                });
    }

    @Nullable
    @Override
    public Integer getProgress() {
        if (mInstallStatus != InstallStatus.DOWNLOADING) {
            return null;
        }
        return percentageDownloaded;
    }

    @Override
    public int getErrorCode() {
        return mInstallErrorCode;
    }

    @Nullable
    @Override
    public Integer availableVersionCode() {
        if (mAppUpdateInfo == null || mUpdateAvailability == UpdateAvailability.UPDATE_NOT_AVAILABLE
                || mUpdateAvailability == UpdateAvailability.UNKNOWN) {
            return null;
        }
        return mAppUpdateInfo.availableVersionCode();
    }
    //endregion

    @Override
    public void onStateUpdate(InstallState state) {
        Logger.log(TAG, "Install state updated with install status: " + state.installStatus()
                + ", and error code: " + state.installErrorCode());
        mInstallErrorCode = state.installErrorCode();
        if (state.installStatus() == InstallStatus.DOWNLOADING) {
            // TODO: Should be keep on publishing status for showing download percentage.
            // From https://stackoverflow.com/a/10415638/6671572
            percentageDownloaded = (int) (state.bytesDownloaded() * 100.0 / state.totalBytesToDownload() + 0.5) ;
        }
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
