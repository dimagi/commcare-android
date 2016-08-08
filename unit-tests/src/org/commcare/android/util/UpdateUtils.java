package org.commcare.android.util;

import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.tasks.InstallStagedUpdateTask;
import org.commcare.tasks.TaskListener;
import org.commcare.tasks.TaskListenerRegistrationException;
import org.commcare.tasks.UpdateTask;
import org.junit.Assert;
import org.robolectric.Robolectric;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateUtils {
    public static void installUpdate(String appFolder,
                                     AppInstallStatus expectedUpdateStatus,
                                     AppInstallStatus expectedInstallStatus) {
        UpdateTask updateTask = stageUpdate(appFolder, expectedUpdateStatus);

        assertEquals(expectedInstallStatus,
                InstallStagedUpdateTask.installStagedUpdate());
        updateTask.clearTaskInstance();
    }

    public static UpdateTask stageUpdate(String profileRef,
                                         AppInstallStatus expectedInstallStatus) {
        UpdateTask updateTask = UpdateTask.getNewInstance();
        try {
            updateTask.registerTaskListener(taskListenerFactory(expectedInstallStatus));
        } catch (TaskListenerRegistrationException e) {
            fail("failed to register listener for update task");
        }
        updateTask.execute(profileRef);

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
        return updateTask;
    }

    public static String buildResourceRef(String baseDir, String app, String resource) {
        return baseDir + app + "/" + resource;
    }

    private static TaskListener<Integer, AppInstallStatus> taskListenerFactory(final AppInstallStatus expectedResult) {
        return new TaskListener<Integer, AppInstallStatus>() {
            @Override
            public void handleTaskUpdate(Integer... updateVals) {
            }

            @Override
            public void handleTaskCompletion(AppInstallStatus result) {
                Assert.assertTrue(result == expectedResult);
            }

            @Override
            public void handleTaskCancellation() {
            }
        };
    }

}
