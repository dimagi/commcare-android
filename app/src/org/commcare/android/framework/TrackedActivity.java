package org.commcare.android.framework;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.analytics.Tracker;

import org.commcare.dalvik.application.CommCareApplication;

/**
 * Any activity for which we want to collect data on user visits
 *
 * @author amstone
 */
public abstract class TrackedActivity extends Activity {

    private static final String KEY_IS_RELOAD = "is-reload";

    private Tracker getTracker() {
        return CommCareApplication._().getDefaultTracker();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!savedInstanceState.getBoolean(KEY_IS_RELOAD)) {
            registerActivityVisit();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_IS_RELOAD, true);
    }

    /**
     * Register a unique user visit to this activity
     */
    private void registerActivityVisit() {
        Log.i("11/6", "Registering visit to: " + getName());
        //getTracker().setScreenName(getName());
        //getTracker().send(new HitBuilders.ScreenViewBuilder().build());
    }

    private String getName() {
        return this.getClass().getSimpleName();
    }
}
