package org.commcare.android.util;

import org.commcare.android.mocks.CommCareTaskConnectorFake;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;
import org.robolectric.Robolectric;

import static org.junit.Assert.fail;

/**
 * Helpers for loading forms into a CommCare app.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SavedFormLoader {
    private static final CommCareTaskConnectorFake<Object> fakeConnector =
            new CommCareTaskConnectorFake<>();

    /**
     * Load saved forms into CommCare app via local payload file.
     *
     * @param payloadFile xml file containing form instances
     */
    public static void loadFormsFromPayload(String payloadFile) {
        TestUtils.processResourceTransaction(payloadFile);

        CommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
        FormRecordCleanupTask<Object> task = new FormRecordCleanupTask<Object>(CommCareApplication._(), platform, -1) {
            @Override
            protected void deliverResult(Object receiver, Integer result) {
            }

            @Override
            protected void deliverUpdate(Object receiver, Integer... values) {
            }

            @Override
            protected void deliverError(Object receiver, Exception e) {
                fail("Failed to load saved forms from file.");
            }


        };
        task.connect(fakeConnector);
        task.execute();
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
    }
}
