package org.commcare.sync;


import static org.commcare.utils.FirebaseMessagingUtil.removeServerUrlFromUserDomain;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.services.CommCareFirebaseMessagingService;
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
        // Retrieve current User
        user = CommCareApplication.instance().getSession().getLoggedInUser();

        // Check if the recipient of the message matches the current logged in user
        // TODO: Decide whether we want to check the validity of the message, based on when it was
        //  created and the date/time of the last sync.
        if (checkUserAndDomain(fcmMessageData.getUsername(), fcmMessageData.getDomain())) {
            // Attempt to trigger the sync, according to the current state of the app
            triggerBackgroundSync();
        } else {
            // Username and Domain don't match current user OR payload data doesn't include username
            // or domain - Action: no actual, just log issue, no need to inform the user
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
        dataPullTask.executeParallel();
    }

    private boolean checkUserAndDomain(String payloadUsername, String payloadDomain) {
        if(payloadUsername != null && payloadDomain != null){
            String loggedInUsername = user.getUsername();
            String userDomain = removeServerUrlFromUserDomain(HiddenPreferences.getUserDomain());
            return payloadUsername.equalsIgnoreCase(loggedInUsername) && payloadDomain.equalsIgnoreCase(userDomain);
        }
        return false;
    }

    @Override
    public void connectTask(CommCareTask task) {

    }

    @Override
    public void startBlockingForTask(int id) {

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
