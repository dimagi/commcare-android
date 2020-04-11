package org.commcare.appupdate;

import com.google.android.play.core.install.InstallStateUpdatedListener;

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
}
