package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.models.database.global.models.ApplicationRecord;
import org.commcare.suite.model.Profile;

/**
 * Created by amstone326 on 3/30/16.
 */
public class MultipleAppsUtil {

    /**
     * @return Is the installation of a new app (that is NOT marked as "ignore", since these can
     * always be installed no matter what) allowed in the current environment?
     */
    public static boolean appInstallationAllowed() {
        for (ApplicationRecord record : CommCareApplication._().getInstalledAppRecords()) {
            // TODO: This check is a bit overkill in the present state, because currently it's not
            // possible that an installed app would have this value, unless it were the only app
            // installed. However, if/once we are pinging the server for this value, it could have changed
            if (record.getMultipleAppsCompatibility().equals(Profile.MULT_APPS_DISABLED_VALUE)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return Would an app that we are attempting to install need to be multiple-apps compatible
     * in order to be installable in the current environment?
     */
    public static boolean multipleAppsCompatibilityRequired() {
        for (ApplicationRecord record : CommCareApplication._().getInstalledAppRecords()) {
            // TODO: in all likelihood we will change this so that for each app, we first ping
            // something on the server to check if there is an updated value, and then fall
            // back to the value that's already in the ApplicationRecord if that's unavailable
            if (!record.getMultipleAppsCompatibility().equals(Profile.MULT_APPS_IGNORE_VALUE)) {
                return true;
            }
        }
        return false;
    }
}
