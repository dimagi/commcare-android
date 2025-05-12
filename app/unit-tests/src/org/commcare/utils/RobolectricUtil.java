package org.commcare.utils;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import org.commcare.activities.CommCareActivity;
import org.commcare.fragments.TaskConnectorViewModel;
import org.commcare.tasks.templates.CommCareTask;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.ExecutionException;

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
        TaskConnectorViewModel stateHolder = new ViewModelProvider(activity).get(TaskConnectorViewModel.class);
        CommCareTask task = stateHolder.getCurrentTask();
        if (task != null) {
            try {
                task.get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException("Current task failed due to " + e.getMessage(), e);
            }
        }
        ShadowLooper.idleMainLooper();
    }
}
