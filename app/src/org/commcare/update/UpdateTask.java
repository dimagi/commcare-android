package org.commcare.update;

import android.content.Context;

import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.SingletonTask;

/**
 * Stages an update for the seated app in the background. Does not perform
 * actual update. If the user opens the Update activity, this task will report
 * its progress to that activity.  Enforces the constraint that only one
 * instance is ever running.
 *
 * Will be cancelled on user logout, but can still run if no user is logged in.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateTask extends SingletonTask<String, Integer, ResultAndError<AppInstallStatus>>
        implements InstallCancelled, UpdateProgressListener {

    private static UpdateTask singletonRunningInstance = null;
    private static final Object lock = new Object();

    private final UpdateHelper mUpdateHelper;


    private UpdateTask() {
        TAG = UpdateTask.class.getSimpleName();
        mUpdateHelper = UpdateHelper.getNewInstance(false, this, this);
    }

    public static UpdateTask getNewInstance() {
        synchronized (lock) {
            if (singletonRunningInstance == null) {
                singletonRunningInstance = new UpdateTask();
                return singletonRunningInstance;
            } else {
                throw new IllegalStateException("An instance of " + TAG + " already exists.");
            }
        }
    }

    public static UpdateTask getRunningInstance() {
        synchronized (lock) {
            if (singletonRunningInstance != null &&
                    singletonRunningInstance.getStatus() == Status.RUNNING) {
                return singletonRunningInstance;
            }
            return null;
        }
    }

    @Override
    protected final ResultAndError<AppInstallStatus> doInBackground(String... params) {
        ResultAndError<AppInstallStatus> result = mUpdateHelper.update(params[0]);

        // onPostExecute doesn't get invoked in case of task cancellation, so process the failure here
        if(result.data == AppInstallStatus.Cancelled){
            mUpdateHelper.OnUpdateComplete(result);
        }
        return result;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        mUpdateHelper.updateNotification(values);
    }

    @Override
    protected void onPostExecute(ResultAndError<AppInstallStatus> resultAndError) {
        super.onPostExecute(resultAndError);
        mUpdateHelper.OnUpdateComplete(resultAndError);
        mUpdateHelper.clearInstance();
    }

    @Override
    protected void onCancelled(ResultAndError<AppInstallStatus> resultAndError) {
        super.onCancelled(resultAndError);
        mUpdateHelper.OnUpdateCancelled();
        mUpdateHelper.clearInstance();
    }

    @Override
    public void clearTaskInstance() {
        synchronized (lock) {
            singletonRunningInstance = null;
            mUpdateHelper.clearInstance();
        }
    }

    /**
     * Allows resource installation process to check if this task was cancelled
     */
    @Override
    public boolean wasInstallCancelled() {
        return isCancelled();
    }

    public int getProgress() {
        return mUpdateHelper.getCurrentProgress();
    }

    public int getMaxProgress() {
        return mUpdateHelper.getMaxProgress();
    }

    public void setLocalAuthority() {
        mUpdateHelper.setLocalAuthority();
    }

    public void clearUpgrade() {
        mUpdateHelper.clearUpgrade();
    }

    public void startPinnedNotification(Context context) {
        mUpdateHelper.startPinnedNotification(context);
    }

    @Override
    public void publishUpdateProgress(int complete, int total) {
        publishProgress(complete, total);
    }
}
