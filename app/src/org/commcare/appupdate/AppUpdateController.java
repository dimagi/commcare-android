package org.commcare.appupdate;

import android.app.Activity;

/**
 * Helper for interacting with playstore AppUpdateManager.
 * @author $|-|!˅@M
 */
public interface AppUpdateController {

    /**
     * Starts an app update, if possible.
     * @param activity The {@link Activity} to use to interact with Google Play.
     */
    void startUpdate(Activity activity);

    /**
     * @return the current state of installation process.
     */
    AppUpdateState getStatus();

    /**
     * Completes the installation process. Might restart the app.
     */
    void completeUpdate();
}
