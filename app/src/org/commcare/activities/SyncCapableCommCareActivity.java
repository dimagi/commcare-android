package org.commcare.activities;

import android.os.Bundle;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormAndDataSyncer;
import org.commcare.activities.SessionAwareCommCareActivity;
import org.commcare.activities.SyncUIHandling;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.PullTaskResultReceiver;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.dialogs.DialogController;
import org.javarosa.core.services.locale.Localization;

public abstract class SyncCapableCommCareActivity<R> extends SessionAwareCommCareActivity<R>
        implements PullTaskResultReceiver {

    protected boolean isSyncUserLaunched = false;
    protected FormAndDataSyncer formAndDataSyncer;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        formAndDataSyncer = new FormAndDataSyncer();
    }

    /**
     * Attempts first to send unsent forms to the server.  If any forms are sent, a sync will be
     * triggered after they are submitted. If no forms are sent, triggers a sync explicitly.
     */
    protected void sendFormsOrSync(boolean userTriggeredSync) {
        boolean formsSentToServer = checkAndStartUnsentFormsTask(true, userTriggeredSync);
        if (!formsSentToServer) {
            formAndDataSyncer.syncDataForLoggedInUser(this, false, userTriggeredSync);
        }
    }

    protected boolean checkAndStartUnsentFormsTask(boolean syncAfterwards, boolean userTriggered) {
        isSyncUserLaunched = userTriggered;
        return formAndDataSyncer.checkAndStartUnsentFormsTask(this, syncAfterwards, userTriggered);
    }

    @Override
    public void handlePullTaskResult(ResultAndError<DataPullTask.PullTaskResult> resultAndError, boolean userTriggeredSync, boolean formsToSend) {
        if (CommCareApplication._().isConsumerApp()) {
            return;
        }
        SyncUIHandling.handleSyncResult(this, resultAndError, userTriggeredSync, formsToSend);
    }

    @Override
    public void handlePullTaskUpdate(Integer... update) {
        SyncUIHandling.handleSyncUpdate(this, update);
    }

    @Override
    public void handlePullTaskError() {
        reportSyncResult(Localization.get("sync.fail.unknown"), false);
    }

    public abstract void reportSyncResult(String message, boolean success);

}
