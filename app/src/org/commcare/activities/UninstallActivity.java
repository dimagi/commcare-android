package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;

/**
 * Created by Saumya on 6/30/2016.
 * Uninstalls whatever app is currently selected and navigates back to SetupActivity
 */
public class UninstallActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        CommCareApp currentApp = CommCareApplication._().getCurrentApp();

        if(currentApp != null){
            uninstallApp(currentApp.getUniqueId());
        }

        Intent i = new Intent(getApplicationContext(), DispatchActivity.class);
        startActivity(i);
    }

    private void uninstallApp(String appId) {
        ApplicationRecord appRecord = CommCareApplication._().getAppById(appId);
        if (appRecord != null) {
            CommCareApplication._().expireUserSession();
            CommCareApplication._().uninstall(appRecord);
           // CommCareApplication.restartCommCare(this, false);
        }
    }
}
