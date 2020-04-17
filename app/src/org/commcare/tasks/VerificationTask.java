package org.commcare.tasks;

import org.commcare.CommCareApplication;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.util.SizeBoundUniqueVector;
import org.javarosa.core.util.SizeBoundVector;

import java.util.Vector;

/**
 * This task is responsible for validating app's installed media
 *
 * @author ctsims
 */
public abstract class VerificationTask<Reciever>
        extends CommCareTask<Void, int[], SizeBoundUniqueVector<MissingMediaException>, Reciever>
        implements TableStateListener, InstallCancelled {

    protected VerificationTask(int taskId) {
        this.taskId = taskId;
    }

    @Override
    protected SizeBoundUniqueVector<MissingMediaException> doTaskBackground(Void... params) {
        AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();

        // This is replicated in the application in a few places.
        ResourceTable global = platform.getGlobalResourceTable();
        Vector<MissingMediaException> problems = new Vector<>();

        setTableListeners(global);
        global.verifyInstallation(problems, platform);
        unsetTableListeners(global);


        // skip lazy resources in verfication
        SizeBoundUniqueVector<MissingMediaException> validProblems = new SizeBoundUniqueVector<>(problems.size());
        for (MissingMediaException problem : problems) {
            if (!problem.getResource().isLazy()) {
                validProblems.add(problem);
            }
        }

        if (validProblems.size() > 0) {
            return validProblems;
        }
        return null;
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
    public void incrementProgress(int complete, int total) {
        this.publishProgress(new int[]{complete, total});
    }

    @Override
    public void compoundResourceAdded(ResourceTable table) {
    }

    @Override
    public void simpleResourceAdded() {
    }

    @Override
    public boolean wasInstallCancelled() {
        return isCancelled();
    }
}
