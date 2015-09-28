package org.commcare.android.resource.installers;

import org.commcare.android.database.global.models.ApplicationRecord;
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
                new ResourceEngineTask<CommCareSetupActivity>(false,
                        app, false,
                        dialogId, false) {

                    @Override
                    protected void deliverResult(CommCareSetupActivity receiver,
                                                 ResourceEngineTask.ResourceEngineOutcomes result) {
                        switch (result) {
                            case StatusInstalled:
                                receiver.reportSuccess(true);
                                break;
                            case StatusUpToDate:
                                receiver.reportSuccess(false);
                                break;
                            case StatusMissingDetails:
                                // fall through to more general case:
                            case StatusMissing:
                                receiver.failMissingResource(this.missingResourceException, result);
                                break;
                            case StatusBadReqs:
                                receiver.failBadReqs(badReqCode, vRequired, vAvailable, majorIsProblem);
                                break;
                            case StatusFailState:
                                receiver.failWithNotification(ResourceEngineOutcomes.StatusFailState);
                                break;
                            case StatusNoLocalStorage:
                                receiver.failWithNotification(ResourceEngineOutcomes.StatusNoLocalStorage);
                                break;
                            case StatusBadCertificate:
                                receiver.failWithNotification(ResourceEngineOutcomes.StatusBadCertificate);
                                break;
                            case StatusDuplicateApp:
                                receiver.failWithNotification(ResourceEngineOutcomes.StatusDuplicateApp);
                                break;
                            default:
                                receiver.failUnknown(ResourceEngineOutcomes.StatusFailUnknown);
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
                        receiver.failUnknown(ResourceEngineOutcomes.StatusFailUnknown);
                    }
                };
        task.connect(activity);
        task.execute(SINGLE_APP_REFERENCE);
    }
}
