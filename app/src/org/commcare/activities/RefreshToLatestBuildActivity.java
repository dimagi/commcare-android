package org.commcare.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.AlertDialogFactory;
import org.javarosa.core.services.locale.Localization;

/**
 * Activity that is launched immediately upon reception of a RefreshToLatestBuildAction broadcast,
 * triggering the necessary action sequence.
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class RefreshToLatestBuildActivity extends Activity {

    public static final String KEY_FROM_LATEST_BUILD_ACTIVITY = "from-test-latest-build-util";
    public static final String KEY_UPDATE_ATTEMPT_RESULT = "result-of-update-attempt";

    // Action status codes
    public static final String UPDATE_SUCCESS = "update-successful";
    public static final String ALREADY_UP_TO_DATE = "already-up-to-date";
    public static final String UPDATE_ERROR = "update-error";
    public static final String UPDATE_CANCELED = "update-canceled";
    public static final String NO_SESSION_ERROR = "no-session-error";

    // Activity request code
    private int PERFORM_UPDATE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.refresh_latest_build_view);
        ((TextView)findViewById(R.id.status_message))
                .setText(Localization.get("refresh.build.base.message"));

        try {
            DevSessionRestorer.tryAutoLoginPasswordSave(getCurrentUserPassword(), true);
            CommCareApplication._().setPendingRefreshToLatestBuild();
            DevSessionRestorer.saveSessionToPrefs();
            attemptUpdate();
        } catch (SessionUnavailableException e) {
            showErrorAlertDialog(NO_SESSION_ERROR);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_CANCELED) {
            showErrorAlertDialog(UPDATE_CANCELED);
        }
        else {
            String status = intent.getStringExtra(KEY_UPDATE_ATTEMPT_RESULT);
            if (UPDATE_SUCCESS.equals(status)) {
                finish();
            } else {
                showErrorAlertDialog(status);
            }
        }
    }

    private void showErrorAlertDialog(String status) {
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
        i.putExtra(KEY_FROM_LATEST_BUILD_ACTIVITY, true);
        startActivityForResult(i, PERFORM_UPDATE);
    }

}
