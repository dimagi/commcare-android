package org.commcare.interfaces;

import android.os.Bundle;

import org.commcare.activities.FormAndDataSyncer;
import org.commcare.activities.SessionAwareCommCareActivity;
import org.commcare.tasks.PullTaskResultReceiver;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.dialogs.DialogController;

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

    public abstract void reportSyncSuccess(String message);

    public abstract void reportSyncFailure(String message, boolean showPopupNotification);
}
