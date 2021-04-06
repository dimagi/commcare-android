package org.commcare.android.util;

import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.tasks.InstallStagedUpdateTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.TaskListener;
import org.commcare.tasks.TaskListenerRegistrationException;
import org.commcare.update.UpdateTask;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.ExecutionException;

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
            updateTask.registerTaskListener(taskListenerFactory(new ResultAndError<>(expectedInstallStatus)));
        } catch (TaskListenerRegistrationException e) {
            fail("failed to register listener for update task");
        }
        try {
            updateTask.execute(profileRef).get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        ShadowLooper.idleMainLooper();
        return updateTask;
    }

    public static String buildResourceRef(String baseDir, String app, String resource) {
        return baseDir + app + "/" + resource;
    }

    private static TaskListener<Integer, ResultAndError<AppInstallStatus>> taskListenerFactory(final ResultAndError<AppInstallStatus> expectedResult) {
        return new TaskListener<Integer, ResultAndError<AppInstallStatus>>() {
            @Override
            public void handleTaskUpdate(Integer... updateVals) {
            }

            @Override
            public void handleTaskCompletion(ResultAndError<AppInstallStatus> result) {
                assertEquals(expectedResult.data, result.data);
            }

            @Override
            public void handleTaskCancellation() {
            }
        };
    }

}
