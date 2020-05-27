package org.commcare.appupdate;

/**
 * Gives the current state of in-app update process when {@link AppUpdateController#getStatus()} is called.
 * @author $|-|!˅@M
 */
public enum AppUpdateState {
    UNAVAILABLE,
    AVAILABLE,
    /** Device is downloading the update. To implement progress bar use {@link AppUpdateController#getProgress()}*/
    DOWNLOADING,
    /** Update is downloaded. Now we need to install the app. Should call {@link AppUpdateController#completeUpdate()}*/
    DOWNLOADED,
    /** App updation failed for some reason. Might be worth to check {@link AppUpdateController#getErrorCode()}*/
    FAILED
}
