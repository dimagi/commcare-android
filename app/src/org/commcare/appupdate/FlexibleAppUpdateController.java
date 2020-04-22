package org.commcare.appupdate;

import androidx.annotation.NonNull;

import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.InstallErrorCode;

import javax.annotation.Nullable;

/**
 * Helper for monitoring flexible app updates using playstore AppUpdateManager.
 * @author $|-|!˅@M
 */
public interface FlexibleAppUpdateController extends AppUpdateController, InstallStateUpdatedListener {
    /**
     * Registers itself to {@link InstallStateUpdatedListener}
     */
    void register();

    /**
     * Unregisters itself to {@link InstallStateUpdatedListener}
     */
    void unregister();

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

}
