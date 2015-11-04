package org.commcare.android.resource.installers;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.dalvik.activities.CommCareSetupActivity;
import org.commcare.dalvik.application.CommCareApp;
import org.javarosa.core.util.PropertyUtils;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SingleAppInstallation {
    private static final String SINGLE_APP_REFERENCE = "jr://asset/direct_install/profile.ccpr";

    /**
     * Install the app present in "assets/direct_install/", without offering
     * any failure modes.  Useful for installing an app automatically
     * without prompting the user.
     */
    public static void installSingleApp(CommCareSetupActivity activity, int dialogId) {
        ApplicationRecord newRecord =
                new ApplicationRecord(PropertyUtils.genUUID().replace("-", ""),
                        ApplicationRecord.STATUS_UNINITIALIZED);

        CommCareApp app = new CommCareApp(newRecord);

        ResourceEngineTask<CommCareSetupActivity> task =
                new ResourceEngineTask<CommCareSetupActivity>(app, dialogId, false) {
                    @Override
                    protected void deliverResult(CommCareSetupActivity receiver,
                                                 AppInstallStatus result) {
                        switch (result) {
                            case Installed:
                                receiver.reportSuccess(true);
                                break;
                            case DuplicateApp:
                                receiver.failWithNotification(AppInstallStatus.DuplicateApp);
                                break;
                            case UpdateStaged:
                                // this should never occur
                                receiver.reportSuccess(false);
                                break;
                            case UpToDate:
                                // this should never occur
                                receiver.reportSuccess(false);
                                break;
                            case MissingResourcesWithMessage:
                                // fall through to more general case:
                            case MissingResources:
                                receiver.failMissingResource(this.missingResourceException, result);
                                break;
                            case IncompatibleReqs:
                                receiver.failBadReqs(badReqCode, vRequired, vAvailable, majorIsProblem);
                                break;
                            case UnknownFailure:
                                receiver.failWithNotification(AppInstallStatus.UnknownFailure);
                                break;
                            case NoLocalStorage:
                                receiver.failWithNotification(AppInstallStatus.NoLocalStorage);
                                break;
                            case NoConnection:
                                receiver.failWithNotification(AppInstallStatus.NoConnection);
                                break;
                            case BadCertificate:
                                receiver.failWithNotification(AppInstallStatus.BadCertificate);
                                break;
                            default:
                                receiver.failUnknown(AppInstallStatus.UnknownFailure);
                                break;
                        }
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
        task.connect(activity);
        task.execute(SINGLE_APP_REFERENCE);
    }
}
