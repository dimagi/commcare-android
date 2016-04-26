package org.commcare.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.AlertDialogFactory;
import org.javarosa.core.services.locale.Localization;

/**
 * Triggers the following action sequence upon launching, which proceeds automatically from
 * start to finish:
 * -Save the current session, including any form entry progress
 * -Attempt to automatically update to the latest build
 * -If the update is successful, log out and log back in as the last user, and then restore the
 * saved session
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class RefreshToLatestBuildActivity extends Activity {

    public static final String KEY_UPDATE_ATTEMPT_RESULT = "result-of-update-attempt";

    // Action status codes
    public static final String UPDATE_SUCCESS = "update-successful";
    public static final String ALREADY_UP_TO_DATE = "already-up-to-date";
    public static final String UPDATE_ERROR = "update-error";
    public static final String UPDATE_CANCELED = "update-canceled";
    private static final String NO_SESSION_ERROR = "no-session-error";
    private static final String SAVING_NOT_ENABLED_ERROR = "session-saving-not-enabled";

    // Activity request code
    private int PERFORM_UPDATE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.refresh_latest_build_view);
        ((TextView)findViewById(R.id.status_message))
                .setText(Localization.get("refresh.build.base.message"));

        if (!DeveloperPreferences.isSessionSavingEnabled()) {
            errorOccurred(SAVING_NOT_ENABLED_ERROR);
            return;
        }

        try {
            DevSessionRestorer.tryAutoLoginPasswordSave(getCurrentUserPassword(), true);
            CommCareApplication._().setPendingRefreshToLatestBuild(true);
            DevSessionRestorer.saveSessionToPrefs();
            attemptUpdate();
        } catch (SessionUnavailableException e) {
            errorOccurred(NO_SESSION_ERROR);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_CANCELED) {
            errorOccurred(UPDATE_CANCELED);
        }
        else {
            String status = intent.getStringExtra(KEY_UPDATE_ATTEMPT_RESULT);
            if (UPDATE_SUCCESS.equals(status)) {
                // UpdateActivity will have expired the session after the successful update,
                // so finishing will take us to the login screen and allow auto-login to proceed
                finish();
            } else {
                errorOccurred(status);
            }
        }
    }

    private void errorOccurred(String status) {
        // Reset this flag to false, since an error occurred and the refresh process is being halted
        CommCareApplication._().setPendingRefreshToLatestBuild(false);

        // Construct an error dialog
        String title = "No Refresh Occurred";
        String message;
        switch(status) {
            case ALREADY_UP_TO_DATE:
                message = Localization.get("refresh.build.up.to.date");
                break;
            case NO_SESSION_ERROR:
                message = Localization.get("refresh.build.session.error");
                break;
            case UPDATE_CANCELED:
                message = Localization.get("refresh.build.update.canceled");
                break;
            case SAVING_NOT_ENABLED_ERROR:
                message = Localization.get("refresh.build.settings.error");
                break;
            case UPDATE_ERROR:
            default:
                message = Localization.get("refresh.build.update.error");
                break;
        }

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        };

        AlertDialogFactory factory = AlertDialogFactory.getBasicAlertFactory(this, title, message, listener);
        factory.showDialog();
    }

    private String getCurrentUserPassword() throws SessionUnavailableException {
        return CommCareApplication._().getSession().getLoggedInUser().getCachedPwd();
    }

    private void attemptUpdate() {
        Intent i = new Intent(this, UpdateActivity.class);
        i.putExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, true);
        startActivityForResult(i, PERFORM_UPDATE);
    }

}
