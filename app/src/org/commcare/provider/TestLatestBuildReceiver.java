package org.commcare.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.commcare.activities.RefreshToLatestBuildActivity;

/**
 * Created by amstone326 on 3/14/16.
 *
 * Trigger from command line with:
 * adb shell am broadcast -a org.commcare.dalvik.api.action.TestLatestBuildAction
 */

public class TestLatestBuildReceiver extends BroadcastReceiver {

    private static final String TAG = TestLatestBuildReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Processing test latest build broadcast");
        Intent i = new Intent(context, RefreshToLatestBuildActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}

