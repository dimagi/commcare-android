package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;

import org.commcare.models.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.locale.Localization;

/**
 * Activity to perform asynchronous initialization of an application
 *
 * @author amstone
 */
public class SeatAppActivity extends Activity {

    private static final String KEY_IN_PROGRESS = "initialization_in_progress";
    private boolean inProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_seat_app);
        TextView tv = (TextView)findViewById(R.id.text);
        tv.setText(Localization.get("seating.app"));

        inProgress = savedInstanceState != null &&
                savedInstanceState.getBoolean(KEY_IN_PROGRESS, false);

        if (!inProgress) {

            String idOfAppToSeat = getIntent().getStringExtra(LoginActivity.KEY_APP_TO_SEAT);
            ApplicationRecord record = CommCareApplication._().getAppById(idOfAppToSeat);

            if (record == null) {
                // No record was found for the given id
                Intent i = new Intent(getIntent());
                setResult(RESULT_CANCELED, i);
                finish();
            }

            ThreadHandler handler = new ThreadHandler(this);
            Thread t = new Thread(new SeatAppProcess(record, handler));
            setInProgress(true);
            t.start();
        }
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

    private static class ThreadHandler extends Handler {

        private final SeatAppActivity activity;

        public ThreadHandler(SeatAppActivity a) {
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

    private static class SeatAppProcess implements Runnable {

        private final ApplicationRecord record;
        private final ThreadHandler handler;

        public SeatAppProcess(ApplicationRecord record, ThreadHandler handler) {
            this.record = record;
            this.handler = handler;
        }

        @Override
        public void run() {
            CommCareApplication._().initializeAppResources(new CommCareApp(this.record));
            handler.sendEmptyMessage(0);
        }
    }
}
