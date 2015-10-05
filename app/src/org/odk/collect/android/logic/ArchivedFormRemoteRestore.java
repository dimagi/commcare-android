package org.odk.collect.android.logic;

import android.widget.Toast;

import org.commcare.android.database.user.models.User;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.tasks.templates.CommCareTaskConnector;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.FormRecordListActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.locale.Localization;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class ArchivedFormRemoteRestore<A extends CommCareActivity> {
    public static final int CLEANUP_ID = 1;

    private final CommCareTaskConnector<A> taskConnector;
    private final A taskReceiver;
    private final CommCarePlatform platform;

    public ArchivedFormRemoteRestore(final CommCareTaskConnector<A> taskConnector,
                                     final A taskReceiver,
                                     final CommCarePlatform platform) {
        this.taskConnector = taskConnector;
        this.taskReceiver = taskReceiver;
        this.platform = platform;
    }

    public void pullArchivedFormsFromServer(String remoteUrl) {
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
        DataPullTask<A> pull = new DataPullTask<A>(u.getUsername(), u.getCachedPwd(), remoteUrl, taskReceiver) {
            @Override
            protected void deliverResult(A receiver, Integer status) {
                switch (status) {
                    case DataPullTask.DOWNLOAD_SUCCESS:
                        FormRecordCleanupTask<A> task = new FormRecordCleanupTask<A>(taskReceiver, platform, CLEANUP_ID) {

                            @Override
                            protected void deliverResult(A receiver, Integer result) {
                                if (receiver instanceof FormRecordListActivity) {
                                    // 'instanceof' testing is my sad hack meant only for dev test code,
                                    // don't re-use or let loose in prod. -- PLM
                                    ((FormRecordListActivity)receiver).refreshView();
                                }
                            }

                            @Override
                            protected void deliverUpdate(A receiver, Integer... values) {
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
                            protected void deliverError(A receiver, Exception e) {
                                Toast.makeText(receiver, Localization.get("activity.task.error.generic", new String[]{e.getMessage()}), Toast.LENGTH_LONG).show();
                            }


                        };
                        task.connect(taskConnector);
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
            protected void deliverUpdate(A receiver, Integer... update) {
                switch (update[0]) {
                    case DataPullTask.PROGRESS_AUTHED:
                        receiver.updateProgress("Authed with server, downloading forms" +
                                        (update[1] == 0 ? "" : " (" + update[1] + ")"),
                                DataPullTask.DATA_PULL_TASK_ID);
                        break;
                }
            }

            @Override
            protected void deliverError(A receiver, Exception e) {
                Toast.makeText(receiver, Localization.get("activity.task.error.generic", new String[]{e.getMessage()}), Toast.LENGTH_LONG).show();
            }
        };
        pull.connect(taskConnector);
        pull.execute();
    }
}
