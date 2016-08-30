package org.commcare.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.commcare.activities.RefreshToLatestBuildActivity;
import org.commcare.preferences.DeveloperPreferences;

/**
 * Receiver for the RefreshToLatestBuildAction broadcast. Trigger from command line with:
 *   adb shell am broadcast -a org.commcare.dalvik.api.action.RefreshToLatestBuildAction
 * or
 *   ./scripts/ccc update
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class RefreshToLatestBuildReceiver extends BroadcastReceiver {

    private static final String TAG = RefreshToLatestBuildReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Processing test latest build broadcast");

        if (intent.getBooleanExtra("useLatestBuild", false)) {
            DeveloperPreferences.enableNewestAppVersion();
        }

        Intent i = new Intent(context, RefreshToLatestBuildActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}

