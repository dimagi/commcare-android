package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;

/**
 * Created by Saumya on 6/30/2016.
 * Janky-ass activity that uninstalls whatever app is currently installed.
 * Doesn't really work.
 */
public class UninstallActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        uninstallApp(CommCareApplication._().getCurrentApp().getUniqueId());
        Intent i = new Intent(this, CommCareSetupActivity.class);
        startActivity(i);
    }

    private static void uninstallApp(String appId) {
        Log.d("UNINSTALL", appId);
        ApplicationRecord appRecord = CommCareApplication._().getAppById(appId);
        if (appRecord != null) {
            CommCareApplication._().expireUserSession();
            CommCareApplication._().uninstall(appRecord);
        }
    }
}
