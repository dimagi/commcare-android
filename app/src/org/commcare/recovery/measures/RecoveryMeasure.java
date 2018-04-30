package org.commcare.recovery.measures;

/**
 * Created by amstone326 on 4/27/18.
 */

public abstract class RecoveryMeasure {

    public static final String REINSTALL_APP = "reinstall-app";
    public static final String UPDATE_APP = "update-app";
    public static final String CLEAR_USER_DATA = "clear-user-data";
    public static final String QUARANTINE_UNSENT_FORMS = "quarantine-unsent-forms";

    private final int uuid;

    public RecoveryMeasure(int uuid) {
        this.uuid = uuid;
    }

    abstract boolean execute();
}
