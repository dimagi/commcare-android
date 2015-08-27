package org.commcare.dalvik.odk.provider;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * Some utility methods used by InstanceProvider and FormsProvider
 *
 * @author amstone
 */
public class ProviderUtils {

    public enum ProviderType {
        TYPE_FORMS, TYPE_INSTANCES;
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
        if (type == ProviderType.TYPE_FORMS) {
            return "forms_" + applicationId + ".db";
        } else {
            return "instances_" + applicationId + ".db";
        }
    }

}
