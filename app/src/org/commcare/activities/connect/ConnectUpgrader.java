package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.views.dialogs.StandardAlertDialog;

import java.io.File;

public class ConnectUpgrader {

    private static final int UPGRADE_CODE_UNKNOWN = 0;
    private static final int UPGRADE_CODE_OK = 1;
    private static final int UPGRADE_CODE_DB_V3 = 2; //Detected Connect DB V3 or earlier
    private static int upgradeCode = UPGRADE_CODE_UNKNOWN;

    public interface UpgradeCallback {
        void upgradeComplete(boolean success);
    }

    public static void checkUpgradeStatus(Context context) {
        //Check for old DB
        File dbFile = context.getDatabasePath(DatabaseConnectOpenHelper.getDbName());
        upgradeCode = dbFile.exists() ? UPGRADE_CODE_DB_V3 : UPGRADE_CODE_OK;
    }

    public static boolean allowConnectUsage() {
        return upgradeCode == UPGRADE_CODE_OK;
    }

    public static void promptToUpgrade(CommCareActivity<?> activity) {
        StandardAlertDialog d = new StandardAlertDialog(activity,
                activity.getString(R.string.connect_upgrade_prompt_title),
                activity.getString(R.string.connect_upgrade_prompt_message));

        d.setPositiveButton(activity.getString(R.string.connect_upgrade_prompt_yes), (dialog, which) -> {
            activity.dismissAlertDialog();

            ConnectTask task = ConnectTask.CONNECT_UPGRADE;
            Intent i = new Intent(activity, task.getNextActivity());
            activity.startActivityForResult(i, task.getRequestCode());
        });

        d.setNegativeButton(activity.getString(R.string.connect_upgrade_prompt_no), (dialog, which) -> {
            activity.dismissAlertDialog();
        });

        activity.showAlertDialog(d);
    }

    public static void startUpgrade(UpgradeCallback callback) {
        AsyncTask<Void, Void, Void> task = new AsyncTask<>() {
            @Override
            protected Void doInBackground(Void... voids) {
                boolean success=  true;
                if(upgradeCode == UPGRADE_CODE_DB_V3) {
                    success = upgradeOldDb();
                }

                //Future upgrades will go here

                if(success) {
                    upgradeCode = UPGRADE_CODE_OK;
                }

                callback.upgradeComplete(success);

                return null;
            }
        };

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static boolean upgradeOldDb() {
        return false;
    }
}
