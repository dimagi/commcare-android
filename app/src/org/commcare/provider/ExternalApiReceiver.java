package org.commcare.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.LoginMode;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.AndroidSharedKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.models.legacy.LegacyInstallUtils;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ExternalManageKeyRecordTask;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * This broadcast receiver is the central point for incoming API calls from other apps.
 * <p/>
 * Right now it's a mess, but at some point we'll go ahead and pull out most of the
 * things you can do here as
 *
 * @author ctsims
 */
public class ExternalApiReceiver extends BroadcastReceiver {

    private final CommCareTaskConnector dummyconnector = new CommCareTaskConnector() {

        @Override
        public void connectTask(CommCareTask task) {
        }

        @Override
        public void startBlockingForTask(int id) {
        }

        @Override
        public void stopBlockingForTask(int id) {
        }

        @Override
        public void taskCancelled() {
        }

        @Override
        public Object getReceiver() {
            return null;
        }

        @Override
        public void startTaskTransition() {
        }

        @Override
        public void stopTaskTransition() {
        }

        @Override
        public void hideTaskCancelButton() {

        }
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.hasExtra(AndroidSharedKeyRecord.EXTRA_KEY_ID)) {
            return;
        }

        String keyId = intent.getStringExtra(AndroidSharedKeyRecord.EXTRA_KEY_ID);
        SqlStorage<AndroidSharedKeyRecord> storage = CommCareApplication.instance().getGlobalStorage(AndroidSharedKeyRecord.class);
        AndroidSharedKeyRecord sharingKey;
        try {
            sharingKey = storage.getRecordForValue(AndroidSharedKeyRecord.META_KEY_ID, keyId);
        } catch (NoSuchElementException nsee) {
            //No valid key record;
            return;
        }

        Bundle b = sharingKey.getIncomingCallout(intent);

        performAction(context, b);
    }

    private void performAction(final Context context, Bundle b) {
        if ("login".equals(b.getString("commcareaction"))) {
            String username = b.getString("username");
            String password = b.getString("password");
            tryLocalLogin(context, username, password);
        } else if ("sync".equals(b.getString("commcareaction"))) {
            boolean formsToSend = checkAndStartUnsentTask(context);
            if (!formsToSend) {
                //No unsent forms, just sync
                syncData(context);
            }
        }
    }

    private boolean checkAndStartUnsentTask(final Context context) {
        try {
            SqlStorage<FormRecord> storage = CommCareApplication.instance().getUserStorage(FormRecord.class);
            Vector<Integer> ids = StorageUtils.getUnsentOrUnprocessedFormsForCurrentApp(storage);
            if (ids.size() > 0) {
                FormRecord[] records = new FormRecord[ids.size()];
                for (int i = 0; i < ids.size(); ++i) {
                    records[i] = storage.read(ids.elementAt(i));
                }
                SharedPreferences settings = CommCareApplication.instance().getCurrentApp().getAppPreferences();
                ProcessAndSendTask<Object> mProcess = new ProcessAndSendTask<Object>(
                        context,
                        settings.getString(CommCareServerPreferences.PREFS_SUBMISSION_URL_KEY,
                                context.getString(R.string.PostURL))) {
                    @Override
                    protected void deliverResult(Object receiver, FormUploadResult result) {
                        if (result == FormUploadResult.FULL_SUCCESS) {
                            //OK, all forms sent, sync time
                            syncData(context);

                        } else if (result == FormUploadResult.FAILURE) {
                            Toast.makeText(context, Localization.get("sync.fail.unsent"), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, Localization.get("sync.fail.unsent"), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    protected void deliverUpdate(Object receiver, Long... update) {
                    }

                    @Override
                    protected void deliverError(Object receiver, Exception e) {
                    }
                };

                mProcess.setListeners(CommCareApplication.instance().getSession().startDataSubmissionListener());
                mProcess.connect(dummyconnector);
                mProcess.execute(records);
                return true;
            } else {
                //Nothing.
                return false;
            }
        } catch (SessionUnavailableException e) {
            Toast.makeText(context, "Couldn't open the database handle. Ensure that your " +
                    "mobile worker is logged into CommCare.", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void syncData(final Context context) {
        try {
            User u = CommCareApplication.instance().getSession().getLoggedInUser();

            SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();

            DataPullTask<Object> mDataPullTask = new DataPullTask<Object>(
                    u.getUsername(),
                    u.getCachedPwd(),
                    prefs.getString(CommCareServerPreferences.PREFS_DATA_SERVER_KEY,
                            context.getString(R.string.ota_restore_url)),
                    context) {

                @Override
                protected void deliverResult(Object receiver, ResultAndError<PullTaskResult> resultAndErrorMessage) {
                    PullTaskResult result = resultAndErrorMessage.data;
                    if (result != PullTaskResult.DOWNLOAD_SUCCESS) {
                        Toast.makeText(context, "CommCare couldn't sync. Please try to sync from CommCare directly for more information", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(context, "CommCare synced!", Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                protected void deliverUpdate(Object receiver, Integer... update) {
                }

                @Override
                protected void deliverError(Object receiver, Exception e) {
                }

            };
            mDataPullTask.connect(dummyconnector);
            mDataPullTask.execute();
        } catch(SessionUnavailableException e) {
            Toast.makeText(context, "Couldn't open the database handle. Ensure that your" +
                    "mobile worker is logged into CommCare.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean tryLocalLogin(Context context, String username, String password) {
        try {
            UserKeyRecord matchingRecord = null;
            for (UserKeyRecord record : CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class)) {
                if (!record.getUsername().equals(username)) {
                    continue;
                }
                String hash = record.getPasswordHash();
                if (hash.contains("$")) {
                    String alg = "sha1";
                    String salt = hash.split("\\$")[1];
                    String check = hash.split("\\$")[2];
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    BigInteger number = new BigInteger(1, md.digest((salt + password).getBytes()));
                    String hashed = number.toString(16);

                    while (hashed.length() < check.length()) {
                        hashed = "0" + hashed;
                    }

                    if (hash.equals(alg + "$" + salt + "$" + hashed)) {
                        matchingRecord = record;
                    }
                }
            }

            if (matchingRecord == null) {
                Toast.makeText(context, "Couldn't log in with username " + username + ". " +
                        "Please confirm your credentials and try again.",
                        Toast.LENGTH_LONG).show();
                return false;
            }
            //TODO: Extract this
            byte[] key = ByteEncrypter.unwrapByteArrayWithString(matchingRecord.getEncryptedKey(), password);
            if (matchingRecord.getType() == UserKeyRecord.TYPE_LEGACY_TRANSITION) {
                LegacyInstallUtils.transitionLegacyUserStorage(context, CommCareApplication.instance().getCurrentApp(), key, matchingRecord);
            }
            //TODO: See if it worked first?

            CommCareApplication.instance().startUserSession(key, matchingRecord, false);
            ExternalManageKeyRecordTask mKeyRecordTask = new ExternalManageKeyRecordTask(context, 0,
                    matchingRecord.getUsername(), password, LoginMode.PASSWORD,
                    CommCareApplication.instance().getCurrentApp(), false);

            mKeyRecordTask.connect(dummyconnector);
            mKeyRecordTask.execute();
            Toast.makeText(context, "Successfully logged in as user " + username + ".", Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to login with exception " + e + ".", Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
