package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.commcare.CommCareApplication;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.utils.SessionUnavailableException;

/**
 * Created by amstone326 on 3/18/16.
 */
public class RefreshToLatestBuildActivity extends Activity {

    private static final String TAG = RefreshToLatestBuildActivity.class.getSimpleName();

    public static final String FROM_LATEST_BUILD_UTIL = "from-test-latest-build-util";

    public static final String UPDATE_ATTEMPT_RESULT = "result-of-update-attempt";
    public static final String UPDATE_SUCCESS = "update-successful";
    public static final String ALREADY_UP_TO_DATE = "already-up-to-date";
    public static final String UPDATE_ERROR = "update-error";

    private int PERFORM_UPDATE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            CommCareApplication._().setPendingRefreshToLatestBuild();
            DevSessionRestorer.tryAutoLoginPasswordSave(getCurrentUserPassword(), true);
            DevSessionRestorer.saveSessionToPrefs();
            performUpdate();
        } catch (SessionUnavailableException e) {
            //TODO: Message that this util cannot be used when logged out
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == PERFORM_UPDATE) {
            finishWithResult(intent.getStringExtra(UPDATE_ATTEMPT_RESULT));
        }
    }

    private void finishWithResult(String result) {
        Intent i = new Intent();
        setResult(RESULT_OK, i);
        i.putExtra(RefreshToLatestBuildActivity.UPDATE_ATTEMPT_RESULT, result);
        finish();
    }

    private String getCurrentUserPassword() throws SessionUnavailableException {
        return CommCareApplication._().getSession().getLoggedInUser().getCachedPwd();
    }

    private void performUpdate() {
        Intent i = new Intent(this, UpdateActivity.class);
        i.putExtra(FROM_LATEST_BUILD_UTIL, true);
        startActivityForResult(i, PERFORM_UPDATE);
    }

}
