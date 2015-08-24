package org.commcare.android.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.AndroidSharedKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.db.legacy.LegacyInstallUtils;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.ManageKeyRecordListener;
import org.commcare.android.tasks.ManageKeyRecordTask;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.tasks.templates.CommCareTaskConnector;
import org.commcare.android.tasks.templates.HttpCalloutTask.HttpCalloutOutcomes;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * This broadcast receiver is the central point for incoming API calls from other apps.
 * 
 * Right now it's a mess, but at some point we'll go ahead and pull out most of the 
 * things you can do here as 
 * 
 * @author ctsims
 *
 */
public class ExternalApiReceiver extends BroadcastReceiver {
    
    CommCareTaskConnector dummyconnector = new CommCareTaskConnector() {

        @Override
        public void connectTask(CommCareTask task) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void startBlockingForTask(int id) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void stopBlockingForTask(int id) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void taskCancelled(int id) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public Object getReceiver() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void startTaskTransition() {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void stopTaskTransition() {
            // TODO Auto-generated method stub
            
        }
        
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        if(!intent.hasExtra(AndroidSharedKeyRecord.EXTRA_KEY_ID)) {
            return;
        }
        
        String keyId = intent.getStringExtra(AndroidSharedKeyRecord.EXTRA_KEY_ID);
        SqlStorage<AndroidSharedKeyRecord> storage = CommCareApplication._().getGlobalStorage(AndroidSharedKeyRecord.class);
        AndroidSharedKeyRecord sharingKey;
        try {
            sharingKey = storage.getRecordForValue(AndroidSharedKeyRecord.META_KEY_ID, keyId);
        } catch(NoSuchElementException nsee) {
            //No valid key record;
            return;
        }
        
        Bundle b = sharingKey.getIncomingCallout(intent);
        
        performAction(context, b);
    }

    private void performAction(final Context context, Bundle b) {
        if(b.getString("commcareaction").equals("login")) {
            String username = b.getString("username");
            String password = b.getString("password");
            tryLocalLogin(context, username, password);
        } else if(b.getString("commcareaction").equals("sync")) {
            
            boolean formsToSend = checkAndStartUnsentTask(context);

            if(!formsToSend) {
                //No unsent forms, just sync
                syncData(context);
            }
            

        }
    }
    
    
    protected boolean checkAndStartUnsentTask(final Context context) {
        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        
        //Get all forms which are either unsent or unprocessed
        Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
        ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
        if(ids.size() > 0) {
            FormRecord[] records = new FormRecord[ids.size()];
            for(int i = 0 ; i < ids.size() ; ++i) {
                records[i] = storage.read(ids.elementAt(i).intValue());
            }
            SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
            ProcessAndSendTask<Object> mProcess = new ProcessAndSendTask<Object>(context, settings.getString("PostURL", context.getString(R.string.PostURL))) {
                @Override
                protected void deliverResult(Object receiver, Integer result) {
                    if(result == FormUploadUtil.FULL_SUCCESS) {
                        //OK, all forms sent, sync time 
                        syncData(context);
                        
                    } else if(result == FormUploadUtil.FAILURE) {
                        Toast.makeText(context, Localization.get("sync.fail.unsent"), Toast.LENGTH_LONG).show();
                    } else  {
                        Toast.makeText(context, Localization.get("sync.fail.unsent"), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                protected void deliverUpdate(Object receiver, Long... update) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                protected void deliverError(Object receiver, Exception e) {
                    // TODO Auto-generated method stub
                    
                }
                
            };

            try {
                mProcess.setListeners(CommCareApplication._().getSession().startDataSubmissionListener());
            } catch (SessionUnavailableException e) {
                // if the session expired don't launch the process
                return false;
            }
            mProcess.connect(dummyconnector);
            mProcess.execute(records);
            return true;
        } else {
            //Nothing.
            return false;
        }
    }

    private void syncData(final Context c) {
        User u;
        try {
            u = CommCareApplication._().getSession().getLoggedInUser();
        } catch (SessionUnavailableException e) {
            // if the session expired don't launch the process
            return;
        }

        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();

        DataPullTask<Object> mDataPullTask = new DataPullTask<Object>(u.getUsername(), u.getCachedPwd(), prefs.getString("ota-restore-url",c.getString(R.string.ota_restore_url)), "", c) {

            @Override
            protected void deliverResult(Object receiver, Integer result) {
                if(result != DataPullTask.DOWNLOAD_SUCCESS) {
                    Toast.makeText(c, "CommCare couldn't sync. Please try to sync from CommCare directly for more information", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(c, "CommCare synced!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            protected void deliverUpdate(Object receiver, Integer... update) {
                // TODO Auto-generated method stub
                
            }

            @Override
            protected void deliverError(Object receiver, Exception e) {
                // TODO Auto-generated method stub
                
            }
            
        };
        mDataPullTask.connect(dummyconnector);
        mDataPullTask.execute();
    }

    
    private boolean tryLocalLogin(Context context, String uname, String password) {
        try{
            UserKeyRecord matchingRecord = null;
            for(UserKeyRecord record : CommCareApplication._().getCurrentApp().getStorage(UserKeyRecord.class)) {
                if(!record.getUsername().equals(uname)) {
                    continue;
                }
                String hash = record.getPasswordHash();
                if(hash.contains("$")) {
                    String alg = "sha1";
                    String salt = hash.split("\\$")[1];
                    String check = hash.split("\\$")[2];
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    BigInteger number = new BigInteger(1, md.digest((salt+password).getBytes()));
                    String hashed = number.toString(16);
                    
                    while(hashed.length() < check.length()) {
                        hashed = "0" + hashed;
                    }
                    
                    if(hash.equals(alg + "$" + salt + "$" + hashed)) {
                        matchingRecord = record;
                    }
                }
            }
            
            if(matchingRecord == null) {
                return false;
            }
            //TODO: Extract this
            byte[] key = CryptUtil.unWrapKey(matchingRecord.getEncryptedKey(), password);
            if(matchingRecord.getType() == UserKeyRecord.TYPE_LEGACY_TRANSITION) {
                LegacyInstallUtils.transitionLegacyUserStorage(context, CommCareApplication._().getCurrentApp(), key, matchingRecord);
            }
            //TODO: See if it worked first?
            
            CommCareApplication._().startUserSession(key, matchingRecord);
            ManageKeyRecordTask mKeyRecordTask = new ManageKeyRecordTask<Object>(context, 0, matchingRecord.getUsername(), password, CommCareApplication._().getCurrentApp(), new ManageKeyRecordListener() {

                @Override
                public void keysLoginComplete(Object o) {

                }

                @Override
                public void keysReadyForSync(Object o) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void keysDoneOther(Object o, HttpCalloutOutcomes outcome) {
                    // TODO Auto-generated method stub

                }

            }) {
                @Override
                protected void deliverUpdate(Object r, String... update) {
                }
            };
            mKeyRecordTask.connect(dummyconnector);
            mKeyRecordTask.execute();
            
            return true;
        }catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
