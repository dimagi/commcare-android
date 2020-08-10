package org.commcare.tasks;

import androidx.annotation.Nullable;

import org.commcare.CommCareApplication;
import org.commcare.activities.RecoveryActivity;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.network.CommcareRequestGenerator;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.InstallRequestSource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnreliableSourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.util.HashMap;
import java.util.Map;

public class ResourceRecoveryTask
        extends CommCareTask<Void, Integer, ResultAndError<AppInstallStatus>, RecoveryActivity> implements TableStateListener, InstallCancelled {

    private static final int RECOVERY_TASK = 10000;
    private static ResourceRecoveryTask singletonRunningInstance = null;
    private static final Object lock = new Object();

    private ResourceRecoveryTask() {
        TAG = ResourceRecoveryTask.class.getSimpleName();
        this.taskId = RECOVERY_TASK;
    }

    public static ResourceRecoveryTask getInstance() {
        synchronized (lock) {
            if (singletonRunningInstance == null) {
                singletonRunningInstance = new ResourceRecoveryTask();
                return singletonRunningInstance;
            } else {
                throw new IllegalStateException("An instance of " + TAG + " already exists.");
            }
        }
    }

    @Nullable
    public static ResourceRecoveryTask getRunningInstance() {
        synchronized (lock) {
            if (singletonRunningInstance != null &&
                    singletonRunningInstance.getStatus() == Status.RUNNING) {
                return singletonRunningInstance;
            }
            return null;
        }
    }

    @Override
    protected ResultAndError<AppInstallStatus> doTaskBackground(Void... voids) {
        AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
        ResourceTable global = platform.getGlobalResourceTable();
        setTableListeners(global);
        ResultAndError<AppInstallStatus> result;
        try {
            RequestStats.register(InstallRequestSource.RECOVERY);
            global.recoverResources(platform, ResourceInstallUtils.getProfileReference(), getRecoveryHeaders());
            result = new ResultAndError(AppInstallStatus.Installed);
            RequestStats.markSuccess(InstallRequestSource.RECOVERY);
        } catch (InstallCancelledException e) {
            result = new ResultAndError(AppInstallStatus.Cancelled, e.getMessage());
        } catch (UnresolvedResourceException e) {
            result = new ResultAndError(ResourceInstallUtils.processUnresolvedResource(e), e.getMessage());
        } catch (UnfullfilledRequirementsException e) {
            result = new ResultAndError(AppInstallStatus.IncompatibleReqs, e.getMessage());
        } finally {
            unsetTableListeners(global);
        }
        return result;
    }

    private Map<String, String> getRecoveryHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put(CommcareRequestGenerator.X_COMMCAREHQ_REQUEST_SOURCE,
                String.valueOf(InstallRequestSource.RECOVERY));
        headers.put(CommcareRequestGenerator.X_COMMCAREHQ_REQUEST_AGE,
                String.valueOf(RequestStats.getRequestAge(InstallRequestSource.RECOVERY)));
        return headers;
    }

    @Override
    protected void onPostExecute(ResultAndError<AppInstallStatus> result) {
        super.onPostExecute(result);
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
    protected void deliverResult(RecoveryActivity recoveryActivity, ResultAndError<AppInstallStatus> result) {
        if (result.data == AppInstallStatus.Installed) {
            recoveryActivity.attemptRecovery();
            recoveryActivity.stopLoading();
        } else {
            recoveryActivity.onRecoveryFailure(result);
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
        Logger.exception("Unknown error while recovering missing resources " + ForceCloseLogger.getStackTrace(e), e);
        recoveryActivity.onRecoveryFailure(new ResultAndError<>(AppInstallStatus.UnknownFailure, e.getMessage()));
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
