package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.DevSessionRestorer;

/**
 * An activity that restores a session
 */
public class RestoreActivity extends Activity {

    public static final String SESSION = "session";
    public static final String FORM = "form";

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        if (!restoreSessionData()) return;

        Intent i = new Intent(getApplicationContext(), CommCareHomeActivity.class);
        i.putExtra(DispatchActivity.WAS_EXTERNAL, true);
        i.putExtra(CommCareHomeActivity.IS_RESTORING, true);
        this.startActivity(i);
        finish();
    }

    private boolean restoreSessionData() {

        String sessionString = getIntent().getStringExtra(SESSION);
        String formEntryString = getIntent().getStringExtra(FORM);

        CommCareApp ccApp = CommCareApplication._().getCurrentApp();
        if (ccApp == null) {
            return false;
        }

        if(sessionString != null){
            ccApp.getAppPreferences().edit()
                    .putString(CommCarePreferences.CURRENT_SESSION, sessionString)
                    .apply();
        }

        if(formEntryString != null){
            ccApp.getAppPreferences().edit()
                    .putString(CommCarePreferences.CURRENT_FORM_ENTRY_SESSION, formEntryString)
                    .apply();
        }

        DevSessionRestorer restorer = new DevSessionRestorer();
        CommCareApplication._().setSessionWrapper(restorer.restoreSessionFromPrefs(CommCareApplication._().getCommCarePlatform()));
        return true;
    }
}
