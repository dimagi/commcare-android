package org.commcare.engine.resource.installers;

import org.commcare.CommCareApp;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.tasks.ResourceEngineTask;

import static org.commcare.engine.resource.ResourceInstallUtils.getNewCommCareApp;
import static org.commcare.engine.resource.ResourceInstallUtils.handleAppInstallResult;

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
        ResourceInstallUtils.startAppInstallAsync(false, dialogId, activity, SINGLE_APP_REFERENCE);
    }
}
