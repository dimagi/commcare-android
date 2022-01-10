package org.commcare.utils;

import org.commcare.activities.CommCareActivity;
import org.commcare.fragments.TaskConnectorFragment;
import org.commcare.tasks.templates.CommCareTask;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.ExecutionException;

import androidx.fragment.app.FragmentManager;

import static android.os.Looper.getMainLooper;
import static org.robolectric.Shadows.shadowOf;

/**
 * @author $|-|!˅@M
 */
public class RobolectricUtil {

    /**
     * A replacement for Robolectric.flushBackgroundThreadScheduler();
     * Uses CommCareActivity to get the current task and then execute it on main thread.
     */
    public static void flushBackgroundThread(CommCareActivity activity) {
        FragmentManager fm = activity.getSupportFragmentManager();
        TaskConnectorFragment stateHolder = (TaskConnectorFragment)fm.findFragmentByTag("state");
        if (stateHolder != null) {
            CommCareTask task = stateHolder.getCurrentTask();
            try {
                task.get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException("Current task failed due to " + e.getMessage(), e);
            }
            ShadowLooper.idleMainLooper();
        }
    }
}
