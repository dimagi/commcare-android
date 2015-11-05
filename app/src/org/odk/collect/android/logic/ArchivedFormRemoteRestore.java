package org.odk.collect.android.logic;

import android.widget.Toast;

import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.FormRecordListActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;

/**
 * Only used for developer debugging.
 *
 * Load saved form instances manually from a xml payload.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class ArchivedFormRemoteRestore {
    public static final int CLEANUP_ID = 1;

    public static void pullArchivedFormsFromServer(String remoteUrl,
                                                   final FormRecordListActivity activity,
                                                   final CommCarePlatform platform) {
        User u;
        try {
            u = CommCareApplication._().getSession().getLoggedInUser();
        } catch (SessionUnavailableException sue) {
            // abort and let default processing happen, since it looks
            // like the session expired.
            return;
        }


        //We should go digest auth this user on the server and see whether to pull them
        //down.
        DataPullTask<FormRecordListActivity> pull = new DataPullTask<FormRecordListActivity>(u.getUsername(), u.getCachedPwd(), remoteUrl, activity) {
            @Override
            protected void deliverResult(FormRecordListActivity receiver, Integer status) {
                switch (status) {
                    case DataPullTask.DOWNLOAD_SUCCESS:
                        FormRecordCleanupTask<FormRecordListActivity> task = new FormRecordCleanupTask<FormRecordListActivity>(activity, platform, CLEANUP_ID) {

                            @Override
                            protected void deliverResult(FormRecordListActivity receiver, Integer result) {
                                receiver.refreshView();
                            }

                            @Override
                            protected void deliverUpdate(FormRecordListActivity receiver, Integer... values) {
                                if (values[0] < 0) {
                                    if (values[0] == FormRecordCleanupTask.STATUS_CLEANUP) {
                                        receiver.updateProgress("Forms Processed. "
                                                + "Cleaning up form records...", CLEANUP_ID);
                                    }
                                } else {
                                    receiver.updateProgress("Forms downloaded. Processing "
                                            + values[0] + " of " + values[1] + "...", CLEANUP_ID);
                                }

                            }

                            @Override
                            protected void deliverError(FormRecordListActivity receiver, Exception e) {
                                Toast.makeText(receiver, Localization.get("activity.task.error.generic", new String[]{e.getMessage()}), Toast.LENGTH_LONG).show();
                            }


                        };
                        task.connect(activity);
                        task.execute();
                        break;
                    case DataPullTask.UNKNOWN_FAILURE:
                        Toast.makeText(receiver, "Failure retrieving or processing data, please try again later...", Toast.LENGTH_LONG).show();
                        break;
                    case DataPullTask.AUTH_FAILED:
                        Toast.makeText(receiver, "Authentication failure. Please logout and resync with the server and try again.", Toast.LENGTH_LONG).show();
                        break;
                    case DataPullTask.BAD_DATA:
                        Toast.makeText(receiver, "Bad data from server. Please talk with your supervisor.", Toast.LENGTH_LONG).show();
                        break;
                    case DataPullTask.CONNECTION_TIMEOUT:
                        Toast.makeText(receiver, "The server took too long to generate a response. Please try again later, and ask your supervisor if the problem persists.", Toast.LENGTH_LONG).show();
                        break;
                    case DataPullTask.SERVER_ERROR:
                        Toast.makeText(receiver, "The server had an error processing your data. Please try again later, and contact technical support if the problem persists.", Toast.LENGTH_LONG).show();
                        break;
                    case DataPullTask.UNREACHABLE_HOST:
                        Toast.makeText(receiver, "Couldn't contact server, please check your network connection and try again.", Toast.LENGTH_LONG).show();
                        break;
                }
            }

            @Override
            protected void deliverUpdate(FormRecordListActivity receiver, Integer... update) {
                switch (update[0]) {
                    case DataPullTask.PROGRESS_AUTHED:
                        receiver.updateProgress("Authed with server, downloading forms" +
                                        (update[1] == 0 ? "" : " (" + update[1] + ")"),
                                DataPullTask.DATA_PULL_TASK_ID);
                        break;
                }
            }

            @Override
            protected void deliverError(FormRecordListActivity receiver, Exception e) {
                Toast.makeText(receiver, Localization.get("activity.task.error.generic", new String[]{e.getMessage()}), Toast.LENGTH_LONG).show();
            }
        };
        pull.connect(activity);
        pull.execute();
    }
}
