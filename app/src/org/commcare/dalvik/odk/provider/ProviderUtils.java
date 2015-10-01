package org.commcare.dalvik.odk.provider;

import org.commcare.dalvik.application.CommCareApp;

/**
 * Some utility methods used by InstanceProvider and FormsProvider, and by the db upgrade methods
 * for both
 *
 * @author amstone
 */
public class ProviderUtils {

    private static CommCareApp currentSandbox;

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

    public static String getProviderDbName(ProviderType type, String applicationId) {
        if (type == ProviderType.FORMS) {
            return "forms_" + applicationId + ".db";
        } else {
            return "instances_" + applicationId + ".db";
        }
    }

    public static void setCurrentSandbox(CommCareApp sandbox) {
        currentSandbox = sandbox;
    }

    public static String getSandboxedAppId() {
        return currentSandbox.getAppRecord().getApplicationId();
    }

}
