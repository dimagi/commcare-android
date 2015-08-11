package org.commcare.dalvik.activities;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * Created by amstone326 on 8/11/15.
 */
public class SeatAppActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_template_printer);

        String idOfAppToSeat = getIntent().getStringExtra(LoginActivity.KEY_APP_TO_SEAT);
        ApplicationRecord record = CommCareApplication._().getAppById(idOfAppToSeat);

        ThreadHandler handler = new ThreadHandler(this);
        Thread t = new Thread(new SeatAppProcess(record, handler));
        t.start();
    }

    private class ThreadHandler extends Handler {

        private Activity activity;

        public ThreadHandler(Activity a) {
            this.activity = a;
        }

        @Override
        public void handleMessage(Message msg) {
            activity.finish();
        }
    }

    private class SeatAppProcess implements Runnable {

        private ApplicationRecord record;
        private ThreadHandler handler;

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
