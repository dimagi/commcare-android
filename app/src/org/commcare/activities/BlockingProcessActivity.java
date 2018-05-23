package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 5/22/18.
 *
 * Abstract activity that can be extended to serve as an activity that blocks the UI with
 * a simple message and progress dialog, while a process is executed on a background thread
 */
public abstract class BlockingProcessActivity extends Activity {

    private static final String KEY_IN_PROGRESS = "initialization_in_progress";
    protected boolean inProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        setContentView(R.layout.blocking_process_screen);
        TextView tv = findViewById(R.id.text);
        tv.setText(Localization.get(getDisplayTextKey()));

        inProgress = savedInstanceState != null &&
                savedInstanceState.getBoolean(KEY_IN_PROGRESS, false);
        if (!inProgress) {
            startProcess();
        }
    }

    private void startProcess() {
        ProcessFinishedHandler handler = new ProcessFinishedHandler(this);
        Runnable process = buildProcessToRun(handler);
        if (process == null) {
            Intent i = new Intent(getIntent());
            setResult(RESULT_CANCELED, i);
            finish();
        } else {
            Thread t = new Thread(process);
            setInProgress(true);
            t.start();
        }
    }

    protected abstract String getDisplayTextKey();
    protected abstract Runnable buildProcessToRun(ProcessFinishedHandler handler);

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_PROGRESS, inProgress);
    }

    @Override
    public void onBackPressed() {
        // Make it impossible to quit in the middle of this activity
    }

    private void setInProgress(boolean b) {
        this.inProgress = b;
    }

    protected static class ProcessFinishedHandler extends Handler {

        private final BlockingProcessActivity activity;

        ProcessFinishedHandler(BlockingProcessActivity a) {
            this.activity = a;
        }

        @Override
        public void handleMessage(Message msg) {
            activity.setInProgress(false);
            Intent i = new Intent(activity.getIntent());
            activity.setResult(RESULT_OK, i);
            activity.finish();
        }
    }

}
