package org.commcare.utils;

import org.commcare.activities.CommCareActivity;
import org.commcare.tasks.templates.CommCareTask;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.ExecutionException;

/**
 * @author $|-|!˅@M
 */
public class RoboelectricUtil {

    /**
     * A replacement for Robolectric.flushBackgroundThreadScheduler();
     * Uses CommCareActivity to get the current task and then execute it on main thread.
     */
    public static void flushBackgroundThread(CommCareActivity activity) {
        CommCareTask task = activity.getCurrentTask();
        try {
            task.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        ShadowLooper.idleMainLooper();
    }
}
