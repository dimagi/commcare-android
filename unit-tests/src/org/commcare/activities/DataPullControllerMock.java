package org.commcare.activities;

import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessage;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class DataPullControllerMock implements DataPullController, CommCareTaskConnector<DataPullController> {

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
    public DataPullController getReceiver() {
        return this;
    }

    @Override
    public void startTaskTransition() {

    }

    @Override
    public void stopTaskTransition() {

    }

    @Override
    public void hideTaskCancelButton() {

    }

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
    public void raiseLoginMessageWithInfo(MessageTag messageTag, String additionalInfo, boolean showTop) {

    }

    @Override
    public void raiseMessage(NotificationMessage message, boolean showTop) {

    }
}
