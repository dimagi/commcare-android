package org.commcare.appupdate;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.play.core.install.model.InstallErrorCode;

import javax.annotation.Nullable;

/**
 * Helper for interacting with playstore AppUpdateManager.
 * @author $|-|!˅@M
 */
public interface AppUpdateController {

    /** Request code for launching in-app update flow. */
    int IN_APP_UPDATE_REQUEST_CODE = 1212;

    /**
     * Starts an app update, if possible.
     * @param activity The {@link Activity} to use to interact with Google Play.
     */
    void startUpdate(Activity activity);

    /**
     * @return the current state of installation process.
     */
    @NonNull
    AppUpdateState getStatus();

    /**
     * Completes the installation process. Might restart the app.
     */
    void completeUpdate();

    /**
     * @return the percentage of total update downloaded.
     * <p>
     *     NOTE: This value is only available when {@link #getStatus} returns {@link AppUpdateState#DOWNLOADING}
     * </p>
     */
    @Nullable
    Integer getProgress();

    /**
     * Returns an error code for the install or {@link InstallErrorCode#NO_ERROR} if there is no error.
     * @return one of the error code from {@link InstallErrorCode}.
     */
    @InstallErrorCode
    int getErrorCode();

    /**
     * If an update is available or in progress, it returns the version code for that update. Otherwise returns null.
     */
    @Nullable
    Integer availableVersionCode();
}
