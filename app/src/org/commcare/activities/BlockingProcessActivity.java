package org.commcare.activities;

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
public abstract class BlockingProcessActivity<T> extends CommCareActivity<T> {

    private static final String KEY_IN_PROGRESS = "initialization_in_progress";
    protected boolean inProgress;

    protected TextView progressTv;
    protected TextView detailTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // todo remove
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        setContentView(R.layout.blocking_process_screen);
        detailTv = findViewById(R.id.detail);
        detailTv.setText(Localization.get(getDisplayTextKey()));
        progressTv = findViewById(R.id.progress_text);
        inProgress = savedInstanceState != null &&
                savedInstanceState.getBoolean(KEY_IN_PROGRESS, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!inProgress) {
            startProcess();
        }
    }

    private void startProcess() {
        Runnable process = buildProcessToRun(new ProcessFinishedHandler(this));
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

    protected void setResultOnIntent(Intent i){
    }

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
            activity.runFinish();
        }
    }

    public void runFinish() {
        setInProgress(false);
        setResultOnIntent(getIntent());
        setResult(RESULT_OK, getIntent());
        finish();
    }
}
