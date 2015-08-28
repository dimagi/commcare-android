package org.commcare.dalvik.odk.provider;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * Some utility methods used by InstanceProvider and FormsProvider, and by the db upgrade methods
 * for both
 *
 * @author amstone
 */
public class ProviderUtils {

    public enum ProviderType {
        FORMS("forms.db"), INSTANCES("instances.db");

        private String oldDbName;

        ProviderType(String oldDbName) {
            this.oldDbName = oldDbName;
        }

        public String getOldDbName() {
            return this.oldDbName;
        }
    }

    public static String getSeatedOrInstallingAppId() {
        CommCareApp currentApp = CommCareApplication._().getCurrentApp();
        if (currentApp != null) {
            return currentApp.getAppRecord().getApplicationId();
        } else {
            return CommCareApplication._().getAppBeingInstalled().getAppRecord().getApplicationId();
        }
    }

    public static String getProviderDbName(ProviderType type, String applicationId) {
        if (type == ProviderType.FORMS) {
            return "forms_" + applicationId + ".db";
        } else {
            return "instances_" + applicationId + ".db";
        }
    }

}
