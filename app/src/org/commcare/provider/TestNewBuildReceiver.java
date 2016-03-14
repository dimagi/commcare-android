package org.commcare.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.tasks.UpdateTask;

/**
 * Created by amstone326 on 3/14/16.
 */
public class TestNewBuildReceiver extends BroadcastReceiver {

    private static final String TAG = TestNewBuildReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        saveSession();
        performUpdate();
        restoreSession();
    }

    private void saveSession() {
        DevSessionRestorer.saveSessionToPrefs();
    }

    private void performUpdate() {
        String ref = ResourceInstallUtils.getDefaultProfileRef();
        try {
            UpdateTask updateTask = UpdateTask.getNewInstance();
            updateTask.setAsAutoUpdate();
            updateTask.execute(ref);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Trying to trigger an update when it is already running. " +
                    "Should only happen if the user triggered a manual update before this fired.");
        }
    }

    private void restoreSession() {

    }
}
