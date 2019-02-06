package org.commcare.engine.resource.installers;

import org.commcare.CommCareApp;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.resources.model.Resource;
import org.commcare.tasks.ResourceEngineTask;

import static org.commcare.activities.CommCareSetupActivity.handleAppInstallResult;

/**
 * Install CC app from the APK's asset directory
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SingleAppInstallation {
    public static final String SINGLE_APP_REFERENCE = "jr://asset/direct_install/profile.ccpr";
    public static final String LOCAL_RESTORE_REFERENCE = "jr://asset/local_restore_payload.xml";

    /**
     * Install the app present in "assets/direct_install/", without offering
     * any failure modes.  Useful for installing an app automatically
     * without prompting the user.
     */
    public static void installSingleApp(CommCareSetupActivity activity, int dialogId) {
        CommCareApp app = CommCareSetupActivity.getCommCareApp();

        ResourceEngineTask<CommCareSetupActivity> task =
                new ResourceEngineTask<CommCareSetupActivity>(app, dialogId, false, Resource.RESOURCE_AUTHORITY_LOCAL, false) {
                    @Override
                    protected void deliverResult(CommCareSetupActivity receiver,
                                                 AppInstallStatus result) {
                        handleAppInstallResult(this,receiver,result);
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
        task.executeParallel(SINGLE_APP_REFERENCE);
    }
}
