package org.commcare.android.framework;

import android.os.Bundle;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import org.commcare.dalvik.application.CommCareApplication;

/**
 * Any CommCareActivity for which we want to collect data on user visits. Identical to
 * TrackedActivity, but needs to be its own class in order to work it into the CommCareActivity
 * hierarchy
 *
 * @author amstone
 */
public abstract class TrackedCommCareActivity<R> extends CommCareActivity<R> {

    private Tracker getTracker() {
        return CommCareApplication._().getDefaultTracker();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            //registerActivityVisit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getTracker().setScreenName(getName());
    }

    /**
     * Register a unique user visit to this activity
     */
    private void registerActivityVisit() {
        //Log.i("11/6", "Registering visit to: " + getName());
        getTracker().setScreenName(getName());
        getTracker().send(new HitBuilders.ScreenViewBuilder().build());
    }

    private String getName() {
        return this.getClass().getSimpleName();
    }
}
