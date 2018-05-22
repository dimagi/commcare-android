package org.commcare.activities;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.utils.MultipleAppsUtil;

/**
 * Activity to perform asynchronous initialization of an application
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class SeatAppActivity extends BlockingProcessActivity {

    public final static String KEY_APP_TO_SEAT = "app_to_seat";

    @Override
    protected String getDisplayTextKey() {
        return "seating.app";
    }

    @Override
    protected Runnable buildProcessToRun(ThreadHandler handler) {
        final ApplicationRecord record =
                MultipleAppsUtil.getAppById(getIntent().getStringExtra(KEY_APP_TO_SEAT));
        if (record == null) {
            return null;
        }

        return () -> {
            CommCareApplication.instance().initializeAppResources(new CommCareApp(record));
            handler.sendEmptyMessage(0);
        };
    }

}
