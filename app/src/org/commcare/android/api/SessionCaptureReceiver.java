package org.commcare.android.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.commcare.android.session.DevSessionRestorer;

/**
 * Process broadcasts requesting to save the current commcare user session.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SessionCaptureReceiver extends BroadcastReceiver {
    private final static String TAG = SessionCaptureReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Processing session capture broadcast");
        DevSessionRestorer.saveSessionToPrefs();
    }
}
