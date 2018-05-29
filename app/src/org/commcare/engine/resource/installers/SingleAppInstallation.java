package org.commcare.engine.resource.installers;

import org.commcare.CommCareApp;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.resources.model.Resource;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.ResourceEngineTask;

import static org.commcare.activities.CommCareSetupActivity.DIALOG_INSTALL_PROGRESS;
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
     * any failure modes. Useful for installing an app automatically without prompting the user.
     */
    public static void installSingleApp(CommCareSetupActivity activity) {
        installSingleApp(activity, null);
    }

    public static <T extends CommCareActivity & ResourceEngineListener> void installSingleApp(
            T activity, String profileRef) {

        profileRef = profileRef == null ? SINGLE_APP_REFERENCE : profileRef;
        CommCareApp app = CommCareSetupActivity.getShellCommCareApp();

        ResourceEngineTask<T> task =
                new ResourceEngineTask<T>(app, DIALOG_INSTALL_PROGRESS, false, Resource.RESOURCE_AUTHORITY_LOCAL) {

            @Override
                    protected void deliverResult(T receiver, AppInstallStatus result) {
                        handleAppInstallResult(this, receiver, result);
                    }

                    @Override
                    protected void deliverUpdate(T receiver, int[]... update) {
                        receiver.updateResourceProgress(update[0][0], update[0][1], update[0][2]);
                    }

                    @Override
                    protected void deliverError(T receiver, Exception e) {
                        receiver.failUnknown(AppInstallStatus.UnknownFailure);
                    }
                };
        task.connect(activity);
        task.executeParallel(profileRef);
    }
}
