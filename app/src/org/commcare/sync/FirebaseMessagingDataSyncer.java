package org.commcare.sync;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.commcare.CommCareApplication;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.services.CommCareSessionService;
import org.commcare.services.FCMMessageData;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.util.LogTypes;
import org.commcare.utils.FirebaseMessagingUtil;
import org.commcare.utils.SyncDetailCalculations;
import org.commcare.views.dialogs.PinnedNotificationWithProgress;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FirebaseMessagingDataSyncer implements CommCareTaskConnector {

    public static final String PENGING_SYNC_ALERT_ACTION = "org.commcare.dalvik.action.PENDING_SYNC_ALERT";
    public static final String FCM_MESSAGE_DATA = "fcm_message_data";
    public static final String FCM_MESSAGE_DATA_KEY = "fcm_message_data_key";
    private static final String SYNC_FORM_SUBMISSION_REQUEST = "background_sync_form_submission_request";
    private Context context;

    public FirebaseMessagingDataSyncer(Context context) {
        this.context = context;
    }

    private CommCareTask currentTask = null;
    private PinnedNotificationWithProgress<DataPullTask.PullTaskResult> mPinnedNotificationProgress = null;

    /**
     * If we land here is because there is a data payload and the action there is to sync
     * 1) Check if there is an active session.
     * - If yes, this will lead to further steps to attempt to trigger a sync
     * - If not, a sync will be scheduled after the next successful login
     * 2) Ensure that the sync is only triggered for the intended 'recipient' of the message
     */
    public void syncData(FCMMessageData fcmMessageData) {
        if (!CommCareApplication.isSessionActive()) {
            //  There is no active session at the moment, proceed accordingly
            HiddenPreferences.setPendingSyncRequestFromServerForUser(fcmMessageData);
            return;
        }
        // Retrieve the current User
        User user = CommCareApplication.instance().getSession().getLoggedInUser();

        // Check if the recipient of the message matches the current logged in user
        if (checkUserAndDomain(user, fcmMessageData.getUsername(), fcmMessageData.getDomain())) {

            if (fcmMessageData.getCreationTime().getMillis() < SyncDetailCalculations.getLastSyncTime(user.getUsername())) {
                // A sync occurred since the sync request was triggered
                return;
            }
            // Attempt to trigger the sync if the user is currently in a sync safe activity
            if (isCurrentActivitySyncSafe()){
                uploadForms(user);
            }
            else {
                informUserAboutPendingSync(fcmMessageData);
            }
        } else {
            // Username and Domain don't match the current user OR payload data doesn't include username
            // and/or domain - Action: no actual, just log issue, no need to inform the user
            Logger.log(LogTypes.TYPE_FCM, "Ignored sync request for " + fcmMessageData.getUsername() + "@" + fcmMessageData.getDomain());
        }
    }

    private void uploadForms(User user) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest formSubmissionRequest = new OneTimeWorkRequest.Builder(FormSubmissionWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .build();

        WorkManager wm = WorkManager.getInstance(CommCareApplication.instance());
        wm.enqueueUniqueWork(SYNC_FORM_SUBMISSION_REQUEST, ExistingWorkPolicy.KEEP,
                formSubmissionRequest);
        LiveData<WorkInfo> workInfoLiveData = wm.getWorkInfoByIdLiveData(formSubmissionRequest.getId());

        // observeForever cannot be called from a background thread
        new Handler(Looper.getMainLooper()).post(() ->
                workInfoLiveData.observeForever(new Observer<>() {
                    @Override
                    public void onChanged(WorkInfo workInfo) {
                        if (workInfo != null && workInfo.getState().isFinished()) {
                            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                triggerBackgroundSync(user);
                            }
                            workInfoLiveData.removeObserver(this);
                        }
                    }
                }));
    }

    /**
     * At this point, all the conditions are in place to trigger the sync: there is an active session
     * and the current user has been successfully verified. The principle here is to:
     * 1) Assess the current state of the app and decide the appropriate course of action, options
     * being:
     * - Trigger a background sync and block the user from accessing features that are not safe
     * during a sync: logouts, syncs and form entries
     * - Schedule the sync right after the form submission
     */
    private void triggerBackgroundSync(User user) {

        DataPullTask<Object> dataPullTask = new DataPullTask<Object>(user.getUsername(),
                user.getCachedPwd(),
                user.getUniqueId(),
                ServerUrls.getDataServerKey(),
                context,
                true) {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected void deliverResult(Object receiver,
                                         ResultAndError<PullTaskResult> resultAndErrorMessage) {
                PullTaskResult result = resultAndErrorMessage.data;
                if (result != DataPullTask.PullTaskResult.DOWNLOAD_SUCCESS) {
                    Toast.makeText(context, Localization.get("background.sync.fail"), Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(context, Localization.get("sync.success.synced"), Toast.LENGTH_LONG).show();
            }

            @Override
            protected void deliverUpdate(Object receiver, Integer... update) {
                handleProgress(update);
            }

            @Override
            protected void deliverError(Object receiver, Exception e) {
                Logger.log(LogTypes.TYPE_FCM, Localization.get("background.sync.fail")+": " + e.getMessage());
            }

            @Override
            protected void onPostExecute(ResultAndError<PullTaskResult> resultAndErrorMessage) {
                super.onPostExecute(resultAndErrorMessage);
                mPinnedNotificationProgress.handleTaskCompletion(resultAndErrorMessage);
            }
        };
        dataPullTask.connect(this);
        dataPullTask.execute();
    }

    private boolean checkUserAndDomain(User user, String payloadUsername, String payloadDomain) {
        if(payloadUsername != null && payloadDomain != null){
            String loggedInUsername = user.getUsername();
            String userDomain = HiddenPreferences.getUserDomainWithoutServerUrl();
            return payloadUsername.equalsIgnoreCase(loggedInUsername) && payloadDomain.equalsIgnoreCase(userDomain);
        }
        return false;
    }

    private boolean isCurrentActivitySyncSafe() {
        return CommCareApplication.backgroundSyncSafe;
    }

    // This method is responsible for informing the User about a pending sync and scheduling
    // a sync for when it's safe
    private void informUserAboutPendingSync(FCMMessageData fcmMessageData) {
        Intent intent = new Intent(PENGING_SYNC_ALERT_ACTION);
        Bundle b = new Bundle();
        b.putSerializable(FCM_MESSAGE_DATA, FirebaseMessagingUtil.serializeFCMMessageData(fcmMessageData));
        intent.putExtra(FCM_MESSAGE_DATA_KEY, b);

        context.sendBroadcast(intent);
    }

    @Override
    public void connectTask(CommCareTask task) {
        this.currentTask = task;
    }

    @Override
    public void startBlockingForTask(int id) {
        // In case a background sync is already ongoing, we ignore the new request silently
        if (CommCareSessionService.sessionAliveLock.isLocked()) {
            currentTask.cancel(true);
        }
        mPinnedNotificationProgress = new PinnedNotificationWithProgress(context,
                "sync.communicating.title","sync.progress.starting", -1);
    }

    @Override
    public void stopBlockingForTask(int id) {
    }

    @Override
    public void taskCancelled() {

    }

    @Override
    public Object getReceiver() {
        return null;
    }

    @Override
    public void startTaskTransition() {

    }

    @Override
    public void stopTaskTransition(int taskId) {
    }

    @Override
    public void hideTaskCancelButton() {

    }

    private void handleProgress(Integer... update) {
        int status = update[0];
        List<Integer> progressBarUpdate = new ArrayList<>();

        switch(status) {
            case DataPullTask.PROGRESS_STARTED:
                mPinnedNotificationProgress.setProgressText("sync.progress.purge");
                break;
            case DataPullTask.PROGRESS_CLEANED:
                mPinnedNotificationProgress.setProgressText("sync.progress.authing");
                break;
            case DataPullTask.PROGRESS_AUTHED:
                mPinnedNotificationProgress.setProgressText("sync.progress.downloading");
                if(update[1] == 1)
                    return;
                break;
            case DataPullTask.PROGRESS_DOWNLOADING:
                mPinnedNotificationProgress.setTitleText("sync.downloading.title");
                mPinnedNotificationProgress.setProgressText("sync.process.downloading.progress");
                progressBarUpdate.add(0);
                progressBarUpdate.add(-1);
                break;
            case DataPullTask.PROGRESS_DOWNLOADING_COMPLETE:
                mPinnedNotificationProgress.setProgressText("sync.process.downloading.progress");
                progressBarUpdate.add(100);
                progressBarUpdate.add(-1);
                break;
            case DataPullTask.PROGRESS_PROCESSING:
                mPinnedNotificationProgress.setTitleText("sync.processing.title");
                mPinnedNotificationProgress.setProgressText("sync.progress");
                progressBarUpdate.add(update[1]);
                progressBarUpdate.add(update[2]);
                break;
            case DataPullTask.PROGRESS_SERVER_PROCESSING:
                mPinnedNotificationProgress.setTitleText("sync.waiting.title");
                mPinnedNotificationProgress.setProgressText("sync.progress");
                progressBarUpdate.add(update[1]);
                progressBarUpdate.add(update[2]);
                break;

            default:
                return;
        }
        mPinnedNotificationProgress.handleTaskUpdate(progressBarUpdate.toArray(new Integer[]{}));
    }
}
