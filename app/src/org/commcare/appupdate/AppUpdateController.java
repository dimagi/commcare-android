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
     * If an update is available or in progress, it returns the version code for that update. Otherwise returns null.
     */
    @Nullable
    Integer availableVersionCode();
}
