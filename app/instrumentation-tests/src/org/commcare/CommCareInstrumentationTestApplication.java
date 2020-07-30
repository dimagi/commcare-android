package org.commcare;

import org.commcare.tasks.AsyncRestoreHelper;
import org.commcare.tasks.DataPullTask;

public class CommCareInstrumentationTestApplication extends CommCareApplication {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public AsyncRestoreHelper getAsyncRestoreHelper(DataPullTask task) {
        return new AsyncRestoreHelperMock(task);
    }
}
