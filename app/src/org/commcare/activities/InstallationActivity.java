package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import org.commcare.CommCareApplication;
import org.commcare.services.CommCareFirebaseMessagingService;
import org.commcare.services.CommCareSessionService;
import org.commcare.utils.SessionUnavailableException;

/**
 * Activity to install apps using push notifications
 */
public class InstallationActivity extends Activity{

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        try{
            CommCareSessionService s = CommCareApplication._().getSession();
            if(s.isActive()){
                CommCareApplication._().expireUserSession();
            }
        }
        catch (SessionUnavailableException e) {
            e.printStackTrace();
        }

        String link = getIntent().getStringExtra(CommCareFirebaseMessagingService.LINK);

        Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
        i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
        i.putExtra(CommCareFirebaseMessagingService.LINK, link);
        this.startActivityForResult(i, DispatchActivity.INIT_APP);

        finish();

    }

}
