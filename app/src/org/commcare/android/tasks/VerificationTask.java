package org.commcare.android.tasks;

import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.util.SizeBoundUniqueVector;
import org.javarosa.core.util.SizeBoundVector;

/**
 * This task is responsible for validating app's installed media
 *
 * @author ctsims
 */
public abstract class VerificationTask<Reciever>
        extends CommCareTask<String, int[], SizeBoundVector<MissingMediaException>, Reciever>
        implements TableStateListener {

    public VerificationTask(int taskId) {
        this.taskId = taskId;
    }

    @Override
    protected SizeBoundVector<MissingMediaException> doTaskBackground(String... profileRefs) {
        AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();

        try {
            // This is replicated in the application in a few places.
            ResourceTable global = platform.getGlobalResourceTable();
            SizeBoundUniqueVector<MissingMediaException> problems =
                    new SizeBoundUniqueVector<>(10);
            global.setStateListener(this);
            global.verifyInstallation(problems);
            if (problems.size() > 0) {
                return problems;
            }
            return null;
        } catch (Exception e) {
            // TODO: make non-resource missing failures have a better exception
            return null;
        }
    }

    @Override
    public void incrementProgress(int complete, int total) {
        this.publishProgress(new int[]{complete, total});
    }

    @Override
    public void resourceStateUpdated(ResourceTable table) {
    }
}
