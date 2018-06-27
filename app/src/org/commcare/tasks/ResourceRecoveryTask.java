package org.commcare.tasks;

import org.commcare.CommCareApplication;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnreliableSourceException;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.AndroidCommCarePlatform;

import java.util.Vector;

public abstract class ResourceRecoveryTask<Reciever>
        extends CommCareTask<Void, Integer, Boolean, Reciever> implements TableStateListener, InstallCancelled {

    public ResourceRecoveryTask(int taskId) {
        this.taskId = taskId;
    }

    @Override
    protected Boolean doTaskBackground(Void... voids) {
        AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
        ResourceTable global = platform.getGlobalResourceTable();
        setTableListeners(global);
        boolean success = global.recoverResources(platform);
        unsetTableListeners(global);
        return success;
    }

    private void setTableListeners(ResourceTable table) {
        table.setStateListener(this);
        table.setInstallCancellationChecker(this);
    }

    private static void unsetTableListeners(ResourceTable table) {
        table.setInstallCancellationChecker(null);
        table.setStateListener(null);
    }

    @Override
    public void simpleResourceAdded() {
    }

    @Override
    public void compoundResourceAdded(ResourceTable table) {
    }

    @Override
    public void incrementProgress(int complete, int total) {
        this.publishProgress(complete);
    }

    @Override
    public boolean wasInstallCancelled() {
        return isCancelled();
    }
}
