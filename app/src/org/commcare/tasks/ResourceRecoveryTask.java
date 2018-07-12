package org.commcare.tasks;

import org.commcare.CommCareApplication;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnreliableSourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

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
        boolean success;
        try {
            success = global.recoverResources(platform, getProfileReference());
        } catch (InstallCancelledException | UnresolvedResourceException | UnfullfilledRequirementsException e) {
            throw new RuntimeException(e);
        } finally {
            unsetTableListeners(global);
        }
        return success;
    }

    /**
     *
     * @return CommCare App Profile url without query params
     */
    private String getProfileReference() {
        String profileRef = ResourceInstallUtils.getDefaultProfileRef();
        return profileRef.split("\\?")[0];

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
        // Do nothing
    }

    @Override
    public void compoundResourceAdded(ResourceTable table) {
        // Do nothing
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
