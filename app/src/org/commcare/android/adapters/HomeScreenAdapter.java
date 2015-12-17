package org.commcare.android.adapters;


import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.commcare.android.view.SquareButtonWithNotification;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;

import java.util.ArrayList;

/**
 * Sets up home screen buttons and gives accessors for setting their visibility and listeners
 * Created by dancluna on 3/19/15.
 */
public class HomeScreenAdapter extends SquareButtonAdapter {
    private static final String TAG = HomeScreenAdapter.class.getSimpleName();
    CommCareHomeActivity activity;

    public HomeScreenAdapter(CommCareHomeActivity activity) {
        super(activity);
        this.activity = activity;
    }

    @Override
    protected SquareButtonObject[] getButtonResources() {

        SquareButtonObject startButton = new SquareButtonObject(activity, R.layout.home_start_button) {
            @Override
            public boolean isHidden() {
                return false;
            }
        };
        SquareButtonObject reportButton = new SquareButtonObject(activity, R.layout.home_report_button) {
            @Override
            public boolean isHidden() {
                return !DeveloperPreferences.isHomeReportEnabled();
            }
        };
        SquareButtonObject savedButton = new SquareButtonObject(activity, R.layout.home_savedforms_button) {
            @Override
            public boolean isHidden() {
                return !CommCarePreferences.isSavedFormsEnabled();
            }
        };
        SquareButtonObject incompleteButton = new SquareButtonObject(activity, R.layout.home_incompleteforms_button) {
            @Override
            public boolean isHidden() {
                return !CommCarePreferences.isIncompleteFormsEnabled();
            }
        };
        SquareButtonObject syncButton = new SquareButtonObject(activity, R.layout.home_sync_button) {
            @Override
            public boolean isHidden() {
                return false;
            }
        };
        SquareButtonObject logoutButton = new SquareButtonObject(activity, R.layout.home_logout_button) {
            @Override
            public boolean isHidden() {
                return false;
            }
        };

        return new SquareButtonObject[]{
                startButton, reportButton, savedButton,
                incompleteButton, syncButton, logoutButton
        };
    }
}
