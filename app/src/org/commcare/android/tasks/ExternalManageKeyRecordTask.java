package org.commcare.android.tasks;

import android.content.Context;

import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.dalvik.activities.DataRestorer;
import org.commcare.dalvik.application.CommCareApp;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class ExternalManageKeyRecordTask extends ManageKeyRecordTask<ExternalManageKeyRecordTask.DummyDataRestorer> {
    public ExternalManageKeyRecordTask(Context c, int taskId, String username, String password,
                               CommCareApp app, boolean restoreSession) {
        super(c, taskId, username, password, app, restoreSession, false);
    }

    @Override
    protected void keysReadyForSync(DummyDataRestorer restorer) {
    }

    @Override
    protected void keysLoginComplete(DummyDataRestorer restorer) {
    }

    @Override
    protected void keysDoneOther(DummyDataRestorer restorer, HttpCalloutOutcomes outcomes) {
    }

    @Override
    protected void deliverUpdate(DummyDataRestorer restorer, String... update) {
    }

    public static class DummyDataRestorer implements DataRestorer {
        @Override
        public void startOta() {

        }

        @Override
        public void done() {

        }

        @Override
        public void raiseLoginMessage(MessageTag messageTag, boolean showTop) {

        }

        @Override
        public void raiseMessage(NotificationMessage message, boolean showTop) {

        }
    }
}
