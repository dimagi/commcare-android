package org.commcare.sync;


import static android.app.Activity.RESULT_CANCELED;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.ENTITY_DETAIL;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.ENTITY_SELECTION;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.FORM_ENTRY;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.HOME_SCREEN;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.MENU;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.OTHER;
import static org.commcare.utils.FirebaseMessagingUtil.removeServerUrlFromUserDomain;

import android.content.Context;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntityDetailActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.MenuActivity;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.services.CommCareFirebaseMessagingService;
import org.commcare.services.CommCareSessionService;
import org.commcare.services.FCMMessageData;
import org.commcare.session.CommCareSession;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.util.LogTypes;
import org.commcare.utils.SyncDetailCalculations;
import org.commcare.views.dialogs.PinnedNotificationWithProgress;
import org.commcare.xml.FixtureIndexSchemaParser;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FirebaseMessagingDataSyncer implements CommCareTaskConnector {

    private Context context;

    public FirebaseMessagingDataSyncer(Context context) {
        this.context = context;
    }

    public enum AppNavigationStates {
        FORM_ENTRY,
        ENTITY_SELECTION,
        ENTITY_DETAIL,
        MENU,
        HOME_SCREEN,
        OTHER
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
            // TODO: Decide whether to only trigger the Sync when the 'recipient' of the message logs in
            //  or anyone, in case multiple users are sharing the same device
            // TODO: Decide whether to check if when there is no active session, the recipient has ever
            //  logged in the device, before scheduling a sync post login
            HiddenPreferences.setPendingSyncRequestFromServer(true);
            HiddenPreferences.setPendingSyncRequestFromServerTime(fcmMessageData.getCreationTime().getMillis());

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
            // Attempt to trigger the sync, according to the current state of the app
            //The current state of the app by checking which activity is in the foreground
            switch (getAppNavigationState()) {
                case FORM_ENTRY:
                    informUserAboutPendingSync(CommCareActivity.currentActivity);
                    break;
                case HOME_SCREEN:
                case ENTITY_SELECTION:
                case ENTITY_DETAIL:
                case MENU:
                case OTHER:
                    triggerBackgroundSync(user);
                    break;
            }
        } else {
            // Username and Domain don't match the current user OR payload data doesn't include username
            // and/or domain - Action: no actual, just log issue, no need to inform the user
            Logger.log(LogTypes.TYPE_FCM, "Invalid data payload");
        }
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

        // TODO: Instead of preventing these from being parsed, we should consider having a flag
        // TODO: to avoid these from being included in the restore file. Similar to the 'Skip
        // TODO: Fixture Syncs on Restore' FF but not at the domain level
        List<String> blocksToSkipParsing = Arrays.asList(new String[]{FixtureIndexSchemaParser.INDICE_SCHEMA, "fixture"});

        DataPullTask<Object> dataPullTask = new DataPullTask<Object>(
                user.getUsername(), user.getCachedPwd(), user.getUniqueId(), ServerUrls.getDataServerKey(), context, blocksToSkipParsing) {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected void deliverResult(Object receiver,
                                         ResultAndError<PullTaskResult> resultAndErrorMessage) {
                PullTaskResult result = resultAndErrorMessage.data;
                if (result != DataPullTask.PullTaskResult.DOWNLOAD_SUCCESS) {
                    Toast.makeText(context, Localization.get("fcm.sync.fail"), Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(context, Localization.get("sync.success.synced"), Toast.LENGTH_LONG).show();
                processReturnFromBackgroundSync();
            }

            @Override
            protected void deliverUpdate(Object receiver, Integer... update) {
                handleProgress(update);
            }

            @Override
            protected void deliverError(Object receiver, Exception e) {
                Logger.log(LogTypes.TYPE_FCM, Localization.get("fcm.sync.fail")+": " + e.getMessage());
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

    private void processReturnFromBackgroundSync() {
        AndroidSessionWrapper aSessWrapper = CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession commcareSession = aSessWrapper.getSession();

        CommCareActivity activity = CommCareActivity.currentActivity;
        boolean recreateActivity = false;

        try {
            switch (getAppNavigationState()) {
                case ENTITY_SELECTION:
                    aSessWrapper.cleanVolatiles();
                    ((EntitySelectActivity) activity).loadEntities();
                    break;
                case ENTITY_DETAIL:
                    aSessWrapper.cleanVolatiles();
                    recreateActivity = true;
                    break;
                case MENU:
                    recreateActivity = true;
                    break;
            }
            commcareSession.syncState();
            if (recreateActivity) {
                activity.recreate();
            }
        }
        catch(RuntimeException e){
            activity.setResult(RESULT_CANCELED);
            activity.finish();
        }
    }

    private boolean checkUserAndDomain(User user, String payloadUsername, String payloadDomain) {
        if(payloadUsername != null && payloadDomain != null){
            String loggedInUsername = user.getUsername();
            String userDomain = removeServerUrlFromUserDomain(HiddenPreferences.getUserDomain());
            return payloadUsername.equalsIgnoreCase(loggedInUsername) && payloadDomain.equalsIgnoreCase(userDomain);
        }
        return false;
    }

    private AppNavigationStates getAppNavigationState() {
        if (CommCareActivity.currentActivity instanceof FormEntryActivity)
            return FORM_ENTRY;
        else if (CommCareActivity.currentActivity instanceof EntitySelectActivity)
            return ENTITY_SELECTION;
        else if (CommCareActivity.currentActivity instanceof EntityDetailActivity)
            return ENTITY_DETAIL;
        else if (CommCareActivity.currentActivity instanceof StandardHomeActivity)
            return HOME_SCREEN;
        else if (CommCareActivity.currentActivity instanceof MenuActivity)
            return MENU;
        return OTHER;
    }

    // This method is responsible for informing the User and scheduling a sync for when it's safe
    private void informUserAboutPendingSync(CommCareActivity activity) {
        activity.runOnUiThread(() ->
                activity.alertPendingSync()
        );
    }

    @Override
    public void connectTask(CommCareTask task) {
        this.currentTask = task;
    }

    @Override
    public void startBlockingForTask(int id) {
        // In case a background sync is already ongoing, we ignore the new request silently by
        // canceling the task
        if (CommCareSessionService.sessionAliveLock.isLocked()) {
            currentTask.cancel(true);
        }
        mPinnedNotificationProgress = new PinnedNotificationWithProgress(context,
                "sync.communicating.title","sync.progress.starting", -1);

        // Disable any pending sync
        HiddenPreferences.setPendingSyncRequestFromServer(false);
    }

    @Override
    public void stopBlockingForTask(int id) {
        // Acquire lock to finalize sync. This is not expected to fail, but in case it happens
        // we will continue
        CommCareSessionService.sessionAliveLock.tryLock();
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
        // Release lock
        if(CommCareSessionService.sessionAliveLock.isLocked()) {
            CommCareSessionService.sessionAliveLock.unlock();
        }
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
