package org.commcare.android.tasks;

import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.ResourceManager;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class InstallStagedUpdateTask<R>
        extends CommCareTask<String, int[], ResourceEngineOutcomes, R> {

    public InstallStagedUpdateTask(int taskId) {
        this.taskId = taskId;
        TAG = InstallStagedUpdateTask.class.getSimpleName();
    }

    @Override
    protected ResourceEngineOutcomes doTaskBackground(String... profileRefs) {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        app.setupSandbox();

        AndroidCommCarePlatform platform = app.getCommCarePlatform();
        ResourceTable global = platform.getGlobalResourceTable();
        ResourceTable temporary = platform.getUpgradeResourceTable();
        ResourceTable recovery = platform.getRecoveryTable();
        ResourceManager resourceManager =
            new ResourceManager(platform, global, temporary, recovery);

        if (!ResourceManager.isTableStaged(temporary)) {
            return ResourceEngineOutcomes.StatusFailState;
        }

        try {
            resourceManager.upgrade();
        } catch (UnresolvedResourceException e) {
        }
        // TODO PLM
        String profileRef = null;
        ResourceInstallUtils.initAndCommitApp(app, profileRef);
        return ResourceEngineOutcomes.StatusInstalled;
    }
}
