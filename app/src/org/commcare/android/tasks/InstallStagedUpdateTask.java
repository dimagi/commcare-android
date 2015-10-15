package org.commcare.android.tasks;

import org.commcare.android.resource.AndroidResourceManager;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.UnresolvedResourceException;

/**
 * Blocks user while performing installation of staged update table.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class InstallStagedUpdateTask<R>
        extends CommCareTask<Void, int[], AppInstallStatus, R> {

    public InstallStagedUpdateTask(int taskId) {
        this.taskId = taskId;
        TAG = InstallStagedUpdateTask.class.getSimpleName();
    }

    @Override
    protected AppInstallStatus doTaskBackground(Void... params) {
        return installStagedUpdate();
    }

    public static AppInstallStatus installStagedUpdate() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        app.setupSandbox();

        AndroidCommCarePlatform platform = app.getCommCarePlatform();
        AndroidResourceManager resourceManager =
                new AndroidResourceManager(platform);

        if (!resourceManager.isUpgradeTableStaged()) {
            resourceManager.recordUpdateInstallFailure(AppInstallStatus.UnknownFailure);
            return AppInstallStatus.UnknownFailure;
        }

        try {
            resourceManager.upgrade();
        } catch (UnresolvedResourceException e) {
            resourceManager.recordUpdateInstallFailure(e);
            return AppInstallStatus.MissingResources;
        }

        ResourceInstallUtils.initAndCommitApp(app);

        return AppInstallStatus.Installed;
    }
}
