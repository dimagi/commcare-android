package org.commcare.utils;

import android.content.Context;
import android.widget.Toast;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.resources.model.Resource;
import org.commcare.tasks.ConnectionDiagnosticTask;
import org.commcare.tasks.ResourceEngineTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.services.locale.Localization;

import static org.commcare.CommCareNoficationManager.AIRPLANE_MODE_CATEGORY;
import static org.commcare.activities.CommCareSetupActivity.DIALOG_INSTALL_PROGRESS;
import static org.commcare.activities.CommCareSetupActivity.INSTALL_MODE_BARCODE;
import static org.commcare.activities.CommCareSetupActivity.INSTALL_MODE_FROM_LIST;
import static org.commcare.activities.CommCareSetupActivity.INSTALL_MODE_MANAGED_CONFIGURATION;
import static org.commcare.activities.CommCareSetupActivity.INSTALL_MODE_OFFLINE;
import static org.commcare.activities.CommCareSetupActivity.INSTALL_MODE_SMS;
import static org.commcare.activities.CommCareSetupActivity.INSTALL_MODE_URL;

/**
 * @author $|-|!˅@M
 */
public class AppInstallationUtil {

    public static void startResourceInstall(Context context, String reference,
                                            CommCareTaskConnector<CommCareSetupActivity> connector,
                                            CommCareApp ccApp, boolean shouldSleep, int lastInstallMode) {
        if (lastInstallMode == INSTALL_MODE_OFFLINE) {
            startResourceInstall(reference, connector, ccApp, shouldSleep, lastInstallMode);
        } else {
            ConnectionDiagnosticTask<CommCareSetupActivity> task = new ConnectionDiagnosticTask<>(context);
            task.setListener(new ConnectionDiagnosticTask.ConnectionDiagnosticListener<CommCareSetupActivity>() {
                @Override
                public void connected(CommCareSetupActivity receiver) {
                    CommCareApplication.notificationManager().clearNotifications(AIRPLANE_MODE_CATEGORY);
                    startResourceInstall(reference, connector, ccApp, shouldSleep, lastInstallMode);
                }

                @Override
                public void failed(CommCareSetupActivity receiver, String errorMessageId,
                                   MessageTag notificationTag, String analyticsMessage) {
                    Toast.makeText(receiver, Localization.get(errorMessageId), Toast.LENGTH_LONG).show();
                    CommCareApplication.notificationManager().reportNotificationMessage(
                            NotificationMessageFactory.message(
                                    notificationTag,
                                    AIRPLANE_MODE_CATEGORY));
                    FirebaseAnalyticsUtil.reportAppInstallFailure(
                            getAnalyticsParamForInstallMethod(lastInstallMode),
                            analyticsMessage);
                }
            });
            task.connect(connector);
            task.executeParallel();
        }
    }

    public static String getAnalyticsParamForInstallMethod(int installModeCode) {
        switch (installModeCode) {
            case INSTALL_MODE_BARCODE:
                return AnalyticsParamValue.BARCODE_INSTALL;
            case INSTALL_MODE_OFFLINE:
                return AnalyticsParamValue.OFFLINE_INSTALL;
            case INSTALL_MODE_SMS:
                return AnalyticsParamValue.SMS_INSTALL;
            case INSTALL_MODE_URL:
                return AnalyticsParamValue.URL_INSTALL;
            case INSTALL_MODE_FROM_LIST:
                return AnalyticsParamValue.FROM_LIST_INSTALL;
            case INSTALL_MODE_MANAGED_CONFIGURATION:
                return AnalyticsParamValue.MANAGED_CONFIG_INSTALL;
            default:
                return "";
        }
    }

    private static void startResourceInstall(String reference, CommCareTaskConnector<CommCareSetupActivity> connector,
                                             CommCareApp ccApp, boolean shouldSleep,
                                             int lastInstallMode) {
        ResourceEngineTask<CommCareSetupActivity> task =
                new ResourceEngineTask<CommCareSetupActivity>(ccApp,
                        DIALOG_INSTALL_PROGRESS, shouldSleep, determineAuthorityForInstall(lastInstallMode), false) {

                    @Override
                    protected void deliverResult(CommCareSetupActivity receiver,
                                                 AppInstallStatus result) {
                        CommCareSetupActivity.handleAppInstallResult(this, receiver, result);
                    }

                    @Override
                    protected void deliverUpdate(CommCareSetupActivity receiver,
                                                 int[]... update) {
                        receiver.updateResourceProgress(update[0][0], update[0][1], update[0][2]);
                    }

                    @Override
                    protected void deliverError(CommCareSetupActivity receiver,
                                                Exception e) {
                        receiver.failUnknown(AppInstallStatus.UnknownFailure);
                    }
                };

        task.connect(connector);
        task.executeParallel(reference);
    }

    private static int determineAuthorityForInstall(int lastInstallMode) {
        // Note that this is an imperfect way to determine the resource authority; we should
        // really be looking at the nature of the reference that is being used itself (i.e. is it
        // a file reference or a URL)
        return lastInstallMode == INSTALL_MODE_OFFLINE ?
                Resource.RESOURCE_AUTHORITY_LOCAL : Resource.RESOURCE_AUTHORITY_REMOTE;
    }

}
