package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.login.AppSeater;
import org.commcare.login.SeatFailure;
import org.commcare.login.SeatResult;
import org.javarosa.core.services.locale.Localization;

/**
 * Activity to perform asynchronous initialization of an application
 *
 * @author amstone
 */
public class SeatAppActivity extends CommonBaseActivity {

    public final static String KEY_APP_TO_SEAT = "app_to_seat";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_seat_app);
        TextView tv = findViewById(R.id.text);
        tv.setText(Localization.get("seating.app"));

        String appId = getIntent().getStringExtra(KEY_APP_TO_SEAT);
        new AppSeater().start(this, appId, progress -> {}, this::finishWithResult);
    }

    private void finishWithResult(SeatResult result) {
        boolean appNotFound = result instanceof SeatResult.Failed
                && ((SeatResult.Failed)result).getReason() == SeatFailure.APP_NOT_FOUND;
        // A corrupted seat still returns RESULT_OK; DispatchActivity detects STATE_CORRUPTED and routes to recovery.
        setResult(appNotFound ? RESULT_CANCELED : RESULT_OK, new Intent(getIntent()));
        finish();
    }

    @Override
    public void onBackPressed() {
        // Make it impossible to quit in the middle of this activity
    }
}
