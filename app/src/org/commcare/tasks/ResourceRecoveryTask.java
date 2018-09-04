package org.commcare.tasks;

import org.commcare.CommCareApplication;
import org.commcare.activities.RecoveryActivity;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnreliableSourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

public class ResourceRecoveryTask
        extends CommCareTask<Void, Integer, Boolean, RecoveryActivity> implements TableStateListener, InstallCancelled {

    private static final int RECOVERY_TASK = 10000;
    private static ResourceRecoveryTask singletonRunningInstance = null;
    private static final Object lock = new Object();

    private ResourceRecoveryTask() {
        this.taskId = RECOVERY_TASK;
    }

    public static ResourceRecoveryTask getInstance() {
        synchronized (lock) {
            if (singletonRunningInstance == null) {
                singletonRunningInstance = new ResourceRecoveryTask();
            }
            return singletonRunningInstance;
        }
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

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        super.onPostExecute(aBoolean);
        clearTaskInstance();
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        clearTaskInstance();
    }

    private void clearTaskInstance() {
        synchronized (lock) {
            singletonRunningInstance = null;
        }
    }

    @Override
    protected void deliverResult(RecoveryActivity recoveryActivity, Boolean success) {
        if (success) {
            recoveryActivity.attemptRecovery();
            recoveryActivity.stopLoading();
        } else {
            recoveryActivity.onRecoveryFailure(R.string.recovery_error_unknown);
        }
    }

    @Override
    protected void deliverUpdate(RecoveryActivity recoveryActivity, Integer... update) {
        int done = update[0];
        int total = update[1];
        recoveryActivity.updateStatus(
                StringUtils.getStringRobust(recoveryActivity, R.string.recovery_resource_progress,
                        new String[]{String.valueOf(done), String.valueOf(total)}));
    }

    @Override
    protected void deliverError(RecoveryActivity recoveryActivity, Exception e) {
        Logger.exception("Error while recovering missing resources " + ForceCloseLogger.getStackTrace(e), e);

        if (e.getCause() instanceof UnreliableSourceException) {
            recoveryActivity.onRecoveryFailure(R.string.recovery_error_poor_connection);
        } else {
            recoveryActivity.onRecoveryFailure(e.getMessage());
        }
    }

    /**
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
        this.publishProgress(complete, total);
    }

    @Override
    public boolean wasInstallCancelled() {
        return isCancelled();
    }
}
