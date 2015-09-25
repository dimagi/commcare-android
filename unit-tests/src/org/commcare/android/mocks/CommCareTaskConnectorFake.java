package org.commcare.android.mocks;

import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.tasks.templates.CommCareTaskConnector;

/**
 * Empty implementation of CommCareTaskConnector for testing
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CommCareTaskConnectorFake<R> implements CommCareTaskConnector<R> {
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
    public void taskCancelled(int id) {

    }

    @Override
    public R getReceiver() {
        return null;
    }

    @Override
    public void startTaskTransition() {

    }

    @Override
    public void stopTaskTransition() {

    }
}
