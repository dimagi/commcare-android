package org.commcare.android.tasks;

import android.content.Context;

import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.dalvik.activities.DataPullController;
import org.commcare.dalvik.application.CommCareApp;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class ExternalManageKeyRecordTask extends ManageKeyRecordTask<ExternalManageKeyRecordTask.DummyDataPullController> {
    public ExternalManageKeyRecordTask(Context c, int taskId, String username, String password,
                               CommCareApp app, boolean restoreSession) {
        super(c, taskId, username, password, app, restoreSession, false);
    }

    @Override
    protected void keysReadyForSync(DummyDataPullController restorer) {
    }

    @Override
    protected void keysLoginComplete(DummyDataPullController restorer) {
    }

    @Override
    protected void keysDoneOther(DummyDataPullController restorer, HttpCalloutOutcomes outcomes) {
    }

    @Override
    protected void deliverUpdate(DummyDataPullController restorer, String... update) {
    }

    public static class DummyDataPullController implements DataPullController {
        @Override
        public void startDataPull() {

        }

        @Override
        public void dataPullCompleted() {

        }

        @Override
        public void raiseLoginMessage(MessageTag messageTag, boolean showTop) {

        }

        @Override
        public void raiseMessage(NotificationMessage message, boolean showTop) {

        }
    }
}
