package org.commcare.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.view.MenuItem;

import org.commcare.fragments.CommCarePreferenceFragment;
import org.commcare.preferences.AdvancedActionsPreferences;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.FormEntryPreferences;

/**
 * Created by dimagi on 26/06/17.
 */

public class CommCarePreferenceActivity extends FragmentActivity {


    public static final String EXTRA_PREF_TYPE = "extra_pref_type";

    //List of Pref Types
    public static final String PREF_TYPE_COMMCARE = "pref_type_commcare";
    public static final String PREF_TYPE_SERVER = "pref_type_server";
    public static final String PREF_TYPE_DEVELOPER = "pref_type_developer";
    public static final String PREF_TYPE_ADVANCED_ACTIONS = "pref_type_advanced_actions";
    public static final String PREF_TYPE_FORM_ENTRY = "pref_type_form_entry";
    public static final String PREF_TYPE_APP_MANAGER_ADVANCED = "pref_type_app_manager_advanced";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addBackButtonToActionBar(this);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_PREF_TYPE)) {
            String prefType = intent.getStringExtra(EXTRA_PREF_TYPE);
            CommCarePreferenceFragment commCarePreferenceFragment;
            switch (prefType) {
                case PREF_TYPE_COMMCARE:
                    commCarePreferenceFragment = new CommCarePreferences();
                    break;
                case PREF_TYPE_SERVER:
                    commCarePreferenceFragment = new CommCareServerPreferences();
                    break;
                case PREF_TYPE_DEVELOPER:
                    commCarePreferenceFragment = new DeveloperPreferences();
                    break;
                case PREF_TYPE_ADVANCED_ACTIONS:
                    commCarePreferenceFragment = new AdvancedActionsPreferences();
                    break;
                case PREF_TYPE_FORM_ENTRY:
                    commCarePreferenceFragment = new FormEntryPreferences();
                    break;
                case PREF_TYPE_APP_MANAGER_ADVANCED:
                    commCarePreferenceFragment = new AppManagerAdvancedPreferences();
                    break;
                default:
                    throw new IllegalStateException("Invalid prefType : " + prefType);
            }

            assert commCarePreferenceFragment != null;

            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, commCarePreferenceFragment)
                    .commit();
        } else {
            throw new IllegalStateException("Must pass an intent with key extra_pref_type to " + CommCarePreferenceActivity.class.getSimpleName());
        }
    }

    public static void addBackButtonToActionBar(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
