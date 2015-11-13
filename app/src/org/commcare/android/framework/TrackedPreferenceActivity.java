package org.commcare.android.framework;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import org.commcare.dalvik.application.CommCareApplication;

/**
 * Created by amstone326 on 11/13/15.
 */
public class TrackedPreferenceActivity extends PreferenceActivity {

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
        getTracker().setScreenName(getName());
        getTracker().send(new HitBuilders.ScreenViewBuilder().build());
    }

    private String getName() {
        return this.getClass().getSimpleName();
    }

}
