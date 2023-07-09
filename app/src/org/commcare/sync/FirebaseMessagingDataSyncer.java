package org.commcare.sync;


import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.ENTITY_DETAIL;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.ENTITY_SELECTION;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.FORM_ENTRY;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.HOME_SCREEN;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.MENU;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.OTHER;
import static org.commcare.utils.FirebaseMessagingUtil.removeServerUrlFromUserDomain;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntityDetailActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.MenuActivity;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.services.CommCareFirebaseMessagingService;
import org.commcare.services.CommCareSessionService;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.util.LogTypes;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;


public class FirebaseMessagingDataSyncer implements CommCareTaskConnector {

    private User user;
    private Context context;
    public FirebaseMessagingDataSyncer(Context context){
        this.context = context;
    }
    public enum AppNavigationStates{
        FORM_ENTRY,
        ENTITY_SELECTION,
        ENTITY_DETAIL,
        MENU,
        HOME_SCREEN,
        OTHER
    }
    private CommCareTask currentTask = null;

    /**
     * If we land here is because there is a data payload and the action there is to sync
     * 1) Check if there is an active session.
     *   - If yes, this will lead to further steps to attempt to trigger a sync
     *   - If not, a sync will be scheduled after the next successful login
     * 2) Ensure that the sync is only triggered for the intended 'recipient' of the message
     *
     */
    public void syncData(CommCareFirebaseMessagingService.FCMMessageData fcmMessageData) {

        if (!CommCareApplication.isSessionActive()){
            //  There is no active session at the moment, proceed accordingly
            // TODO: Decide whether to only trigger the Sync when the 'recipient' of the message logs in
            //  or anyone, in case multiple users are sharing the same device
            // TODO: Decide whether to check if when there is no active session, the recipient has ever
            //  logged in the device, before scheduling a sync post login
            HiddenPreferences.setPendingSyncRequestFromServer(true);
            return;
        }

        // Retrieve the current User
        user = CommCareApplication.instance().getSession().getLoggedInUser();

        // Check if the recipient of the message matches the current logged in user
        // TODO: Decide whether we want to check the validity of the message, based on when it was
        //  created and the date/time of the last sync.
        if (checkUserAndDomain(fcmMessageData.getUsername(), fcmMessageData.getDomain())) {

            // Attempt to trigger the sync, according to the current state of the app
            //The current state of the app by checking which activity is in the foreground
            switch (getAppNavigationState()){
                case FORM_ENTRY:
                    informUserAboutPendingSync(CommCareActivity.currentActivity);
                    break;
                case HOME_SCREEN:
                case ENTITY_SELECTION:
                case ENTITY_DETAIL:
                case MENU:
                case OTHER:
                    triggerBackgroundSync();
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
     *  - Trigger a background sync and block the user from initiating any feature that are not
     *    considered safe during a sync
     *  - Based on user input, go to the Home screen and trigger a blocking sync
     *  - Schedule the sync right after the form submission
     */
    private void triggerBackgroundSync() {
        DataPullTask<Object> dataPullTask = new DataPullTask<Object>(
                user.getUsername(), user.getCachedPwd(), user.getUniqueId(), ServerUrls.getDataServerKey(), context) {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected void deliverResult(Object receiver,
                                         ResultAndError<PullTaskResult> resultAndErrorMessage) {
            }

            @Override
            protected void deliverUpdate(Object receiver, Integer... update) {
            }

            @Override
            protected void deliverError(Object receiver, Exception e) {
            }

            @Override
            protected void onPostExecute(ResultAndError<PullTaskResult> resultAndErrorMessage) {
                super.onPostExecute(resultAndErrorMessage);
            }
        };
        dataPullTask.connect(this);
        dataPullTask.execute();
    }

    private boolean checkUserAndDomain(String payloadUsername, String payloadDomain) {
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
}
