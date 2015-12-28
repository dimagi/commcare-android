package org.commcare.dalvik.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.AndroidShortcuts;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.application.InitializationHelper;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DispatchActivity extends FragmentActivity {
    private static final String SESSION_REQUEST = "ccodk_session_request";
    public static final String WAS_EXTERNAL = "launch_from_external";

    public static final int INIT_APP = 8;
    /**
     * Request code for automatically validating media from home dispatch.
     * Should signal a return from CommCareVerificationActivity.
     */
    public static final int MISSING_MEDIA_ACTIVITY=256;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dispatch();
    }
    private void dispatch() {
        InitializationHelper.checkDbState(this);

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
            // Note that the order in which these conditions are checked matters!!
            try {
                ApplicationRecord currentRecord = currentApp.getAppRecord();
                if (currentApp.getAppResourceState() == CommCareApplication.STATE_CORRUPTED) {
                    // Path 1a: The seated app is damaged or corrupted
                    InitializationHelper.handleDamagedApp(this);
                } else if (!currentRecord.isUsable()) {
                    // Path 1b: The seated app is unusable (means either it is archived or is
                    // missing its MM or both)
                    boolean unseated = handleUnusableApp(currentRecord);
                    if (unseated) {
                        // Recurse in order to make the correct decision based on the new state
                        dispatch();
                    }
                } else if (!CommCareApplication._().getSession().isActive()) {
                    // Path 1c: The user is not logged in
                    // TODO PLM
                    // launchLogin();
                } else if (this.getIntent().hasExtra(SESSION_REQUEST)) {
                    // Path 1d: CommCare was launched from an external app, with a session descriptor
                    handleExternalLaunch();
                } else if (this.getIntent().hasExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT)) {
                    // Path 1e: CommCare was launched from a shortcut
                    handleShortcutLaunch();
                }
            } catch (SessionUnavailableException sue) {
                // TODO PLM
                //launchLogin();
            }
        }
    }

    /**
     *
     * @param record the ApplicationRecord corresponding to the seated, unusable app
     * @return if the unusable app was unseated by this method
     */
    private boolean handleUnusableApp(ApplicationRecord record) {
        if (record.isArchived()) {
            // If the app is archived, unseat it and try to seat another one
            CommCareApplication._().unseat(record);
            CommCareApplication._().initFirstUsableAppRecord();
            return true;
        }
        else {
            // This app has unvalidated MM
            if (CommCareApplication._().usableAppsPresent()) {
                // If there are other usable apps, unseat it and seat another one
                CommCareApplication._().unseat(record);
                CommCareApplication._().initFirstUsableAppRecord();
                return true;
            } else {
                handleUnvalidatedApp();
                return false;
            }
        }
    }

    /**
     * Handles the case where the seated app is unvalidated and there are no other usable apps
     * to seat instead -- Either calls out to verification activity or quits out of the app
     */
    private void handleUnvalidatedApp() {
        if (CommCareApplication._().shouldSeeMMVerification()) {
            Intent i = new Intent(this, CommCareVerificationActivity.class);
            this.startActivityForResult(i, MISSING_MEDIA_ACTIVITY);
        } else {
            // Means that there are no usable apps, but there are multiple apps who all don't have
            // MM verified -- show an error message and shut down
            CommCareApplication._().triggerHandledAppExit(this,
                    Localization.get("multiple.apps.unverified.message"),
                    Localization.get("multiple.apps.unverified.title"));
        }
    }

    private void handleExternalLaunch() {
        String sessionRequest = this.getIntent().getStringExtra(SESSION_REQUEST);
        SessionStateDescriptor ssd = new SessionStateDescriptor();
        ssd.fromBundle(sessionRequest);
        CommCareApplication._().getCurrentSessionWrapper().loadFromStateDescription(ssd);
        Intent i = new Intent(this, CommCareHomeActivity.class);
        i.putExtra(WAS_EXTERNAL, true);
        startActivity(i);
    }

    private void handleShortcutLaunch() {
        //We were launched in shortcut mode. Get the command and load us up.
        CommCareApplication._().getCurrentSession().setCommand(
                this.getIntent().getStringExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT));
        // TODO PLM
        // sessionNavigator.startNextSessionStep();
        //Only launch shortcuts once per intent
        this.getIntent().removeExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT);
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
            case MISSING_MEDIA_ACTIVITY:
                if(resultCode == RESULT_CANCELED){
                    // exit the app if media wasn't validated on automatic
                    // validation check.
                    this.finish();
                    return;
                } else if(resultCode == RESULT_OK){
                    Toast.makeText(this, "Media Validated!", Toast.LENGTH_LONG).show();
                    return;
                }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }
}
