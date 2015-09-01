package org.commcare.android.database;

/**
 * Exception thrown when an error is encountered during global db upgrades
 *
 * @author amstone
 */
public class MigrationException extends RuntimeException {

    public static final String DEFINITE_FAILURE_MESSAGE = "CommCare was unable to to migrate your " +
            "app data for an upgrade. To resolve this issue, please uninstall CommCare ODK from " +
            "your device and then relaunch it.";
    public static final String POSSIBLE_FAILURE_MESSAGE = "CommCare was unable to migrate all of your data " +
            "during an application upgrade. Please restart CommCare to try to fix this. If this message " +
            "persists, you may need to 'Clear data' for CommCare ODK in your device's Settings menu.";
    public static final String FAILURE_TITLE = "Migration Error";

    private boolean definiteFailure;

    public MigrationException(boolean b) {
        this.definiteFailure = b;
    }

    public boolean isDefiniteFailure() {
        return this.definiteFailure;
    }

}
