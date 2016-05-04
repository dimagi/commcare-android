package org.commcare.android.mocks;

import org.commcare.android.util.TestResourceEngineTaskListener;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;

/**
 * Empty implementation of CommCareTaskConnector for testing
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class MockCommCareTaskConnector<R> implements CommCareTaskConnector<R> {

    private R receiver;

    public MockCommCareTaskConnector() {

    }

    private MockCommCareTaskConnector(R receiver) {
        this.receiver = receiver;
    }

    public static MockCommCareTaskConnector<TestResourceEngineTaskListener> getTaskConnectorWithReceiver(TestResourceEngineTaskListener receiver) {
        return new MockCommCareTaskConnector(receiver);
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
    public R getReceiver() {
        return receiver;
    }

    @Override
    public void startTaskTransition() {

    }

    @Override
    public void stopTaskTransition() {

    }
}
