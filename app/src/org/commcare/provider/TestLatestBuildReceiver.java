package org.commcare.provider;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.activities.RefreshToLatestBuildActivity;
import org.commcare.dalvik.R;
import org.commcare.views.dialogs.AlertDialogFactory;

/**
 * Created by amstone326 on 3/14/16.
 *
 * IMPORTANT: At least for now, session saving must be enabled for this to work properly
 *
 * Trigger from command line with:
 * adb shell am broadcast -a org.commcare.dalvik.api.action.TestLatestBuildAction
 */

public class TestLatestBuildReceiver extends BroadcastReceiver {

    private static final String TAG = TestLatestBuildReceiver.class.getSimpleName();

    public static final int LAUNCH_BUILD_REFRESH = 9999; // high number that won't conflict; this is kinda sketch

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Processing test latest build broadcast");
        showActionWarning((Activity)context);
    }

    private static void showActionWarning(final Activity contextActivity) {
        String title = "Refresh to Latest Build?";
        String message = "Proceeding will trigger an automatic update to the latest build of your" +
                "app, and then restore your current session. Do you wish to proceed?";
        AlertDialogFactory factory = new AlertDialogFactory(contextActivity, title, message);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    launchRefreshBuildActivity(contextActivity);
                }
            }

        };
        factory.setPositiveButton("OK", listener);
        factory.setNegativeButton("CANCEL", listener);
        factory.showDialog();
    }

    private static void launchRefreshBuildActivity(Activity contextActivity) {
        Intent i = new Intent(contextActivity, RefreshToLatestBuildActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        contextActivity.startActivityForResult(i, LAUNCH_BUILD_REFRESH);
    }
}

