package org.commcare.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.commcare.home.HomeRequestCodes;
import org.commcare.utils.SessionRegistrationHelper;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SessionAwarePreferenceActivity extends CommCarePreferenceActivity {

    @Override
    protected void onResume() {
        super.onResume();
        SessionRegistrationHelper.registerSessionExpirationReceiver(this);
        SessionRegistrationHelper.handleSessionExpiration(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        SessionRegistrationHelper.unregisterSessionExpirationReceiver(this);
    }

    /**
     * The CommCare settings screen, which reports back under {@link HomeRequestCodes#PREFERENCES_ACTIVITY}.
     */
    public static Intent buildCommCareSettingsIntent(Context context) {
        return new Intent(context, SessionAwarePreferenceActivity.class)
                .putExtra(EXTRA_PREF_TYPE, PREF_TYPE_COMMCARE);
    }

    public static void launchCommCareSettings(Activity activity) {
        activity.startActivityForResult(
                buildCommCareSettingsIntent(activity),
                HomeRequestCodes.PREFERENCES_ACTIVITY
        );
    }
}
