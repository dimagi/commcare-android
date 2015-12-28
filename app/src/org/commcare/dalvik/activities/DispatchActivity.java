package org.commcare.dalvik.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DispatchActivity extends FragmentActivity {
    public static final int INIT_APP = 8;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CommCareApp currentApp = CommCareApplication._().getCurrentApp();

        if (currentApp == null) {
            // no app present, launch setup activity
            if (CommCareApplication._().usableAppsPresent()) {
                // This is BAD -- means we ended up at home screen with no seated app, but there
                // are other usable apps available. Should not be able to happen.
                Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "In CommCareHomeActivity with no" +
                        "seated app, but there are other usable apps available on the device.");
            }
            Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
            this.startActivityForResult(i, INIT_APP);
        } else {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // if handling new return code (want to return to home screen) but a return at the end of your statement
        switch (requestCode) {
            case INIT_APP:
                if (resultCode == RESULT_CANCELED) {
                    // User pressed back button from install screen, so take them out of CommCare
                    this.finish();
                    return;
                } else if (resultCode == RESULT_OK) {
                    // TODO PLM
                    return;
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }
}
