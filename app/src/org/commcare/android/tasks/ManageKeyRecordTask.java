package org.commcare.android.tasks;

import android.content.Context;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.UserSandboxUtils;
import org.javarosa.core.model.User;
import org.commcare.android.db.legacy.LegacyInstallUtils;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.tasks.templates.HttpCalloutTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.KeyRecordParser;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * This task is responsible for taking user credentials and attempting to
 * log in a user with local data. If the credentials represent a user who
 * doesn't exist, this task will attempt to fetch and create a key record
 * for the user specified.
 * 
 * This task uses three steps
 * 1) Clean up user key records and figure out whether we need to look for
 * new records
 * 2) Fetch new records [HTTP step] (not always executed)
 * 3) Process the new records and perform any necessary data migration
 * 
 * @author ctsims
 *
 */
public abstract class ManageKeyRecordTask<R> extends HttpCalloutTask<R> {
    private final String username;
    private String password;
    private final String pin;
    private final boolean inPinMode;
    
    final CommCareApp app;
    
    String keyServerUrl;
    
    ArrayList<UserKeyRecord> keyRecords;
    
    final ManageKeyRecordListener<R> listener;
    
    boolean userRecordExists;

    boolean calloutNeeded;
    boolean calloutSuccessRequired;

    final boolean restoreSession;
    
    User loggedIn = null;
    
    public ManageKeyRecordTask(Context c, int taskId, String username, String passwordOrPin,
                               boolean inPinMode, CommCareApp app, boolean restoreSession,
                               ManageKeyRecordListener<R> listener) {
        super(c);
        this.username = username;
        this.inPinMode = inPinMode;
        if (inPinMode) {
            this.pin = passwordOrPin;
            this.password = null;
        } else {
            this.password = passwordOrPin;
            this.pin = null;
        }
        this.app = app;
        this.restoreSession = restoreSession;
        
        keyServerUrl = app.getAppPreferences().getString("key_server", null);
        //long story
        keyServerUrl = "".equals(keyServerUrl) ? null : keyServerUrl;
        
        this.listener = listener;
        this.taskId = taskId;
    }

    @Override
    protected void deliverResult(R receiver, HttpCalloutOutcomes result) {        
        // If this task completed and we logged in.
        if (result == HttpCalloutOutcomes.Success) {
            if (loggedIn == null) {
                //If we got here, we didn't "log in" fully. IE: We have a key record and a
                //functional sandbox, but this user has never been synced, so we aren't
                //really "logged in".
                CommCareApplication._().releaseUserResourcesAndServices();
                listener.keysReadyForSync(receiver);
                return;
            } else {
                listener.keysLoginComplete(receiver);
                return;
            }
        } else if (result == HttpCalloutOutcomes.NetworkFailure) {
            if (calloutNeeded && userRecordExists) {
                result = HttpCalloutOutcomes.NetworkFailureBadPassword;
            }
        }

        //For any other result make sure we're logged out.
        CommCareApplication._().releaseUserResourcesAndServices();

        //TODO: Do we wanna split this up at all? Seems unlikely. We don't have, like, a ton
        //more context that the receiving activity will
        listener.keysDoneOther(receiver, result);
    }

    @Override
    protected void deliverError(R receiver, Exception e) {
        Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Error executing task in background: " + e.getMessage());
        listener.keysDoneOther(receiver, HttpCalloutOutcomes.UnknownError);
    }


    @Override
    protected HttpCalloutOutcomes doSetupTaskBeforeRequest() {
        /**
         * This step needs to determine three things:
         * 1) Whether we are doing remote key management
         * 2) Whether we should look for new key records
         * 3) Whether we _need_ new key records, or can proceed without them if the fetch fails.
         */
        
        // Clean up the existing key records and make sure we're in a consistent state
        cleanupUserKeyRecords();
        
        // Now, see whether we have a valid record for this username/password/pin combo
        boolean hasRecord = false;
        userRecordExists = false;
        UserKeyRecord valid = null;
        
        SqlStorage<UserKeyRecord> storage = app.getStorage(UserKeyRecord.class);
        for (UserKeyRecord ukr : storage.getRecordsForValue(UserKeyRecord.META_USERNAME, username)) {
            userRecordExists = true;

            if (!ukr.isPasswordOrPinValid(password, pin)) {
                continue;
            }
            
            // Regardless of whether it's "valid", we have a record
            hasRecord = true;
            
            // Now check whether this record is fully valid, or we need to look for an update
            if (ukr.isCurrentlyValid()) {
                valid = ukr;
            }
        }

        if (!hasRecord && keyServerUrl == null) {
            // If we don't have any records and we aren't doing remote key management, this is as
            // far as we're going
            return HttpCalloutOutcomes.Success;
        }

        /* Should only try to look for new records if ALL of the following are true:
         * a) We're not in pin mode (otherwise, should only be try matching to an existing record on the device)
         * b) We didn't find a matching record that is valid
         * c) There is a keyServerUrl to make the http callout to */
        //TODO AMS: Currently set this up that we will just never do a callout if in pin mode, but
        // it's possible that we do want to try to update an invalid record? -- Likely not though
        // b/c would need password
        calloutNeeded = !inPinMode && (!hasRecord || valid == null) && keyServerUrl != null;
        
        if (calloutNeeded) {
            calloutSuccessRequired = !hasRecord;
            Logger.log(AndroidLogger.TYPE_USER, "Performing key record callout." + (calloutSuccessRequired ? " Success is required for login" : ""));
            this.publishProgress(Localization.get("key.manage.callout"));
        }
        
        return null;
    }

    private void cleanupUserKeyRecords() {
        UserKeyRecord currentlyValid = null;
        //For all "new" entries: If there's another sandbox record (regardless of user)
        //which shares the sandbox ID, we can set the status of the new record to be
        //the same as the old record.
        
        SqlStorage<UserKeyRecord> storage = app.getStorage(UserKeyRecord.class);

        for (UserKeyRecord normalRecord :
                storage.getRecordsForValue(UserKeyRecord.META_KEY_STATUS, UserKeyRecord.TYPE_NORMAL)) {

            if (normalRecord.getUsername().equals(username) && normalRecord.isCurrentlyValid()
                    && normalRecord.isPasswordOrPinValid(password, pin)) {
                if (currentlyValid == null) {
                    currentlyValid = normalRecord;
                } else {
                    Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "User " + username + " has more than one currently valid key record!!");
                }
            }
        }

        for (UserKeyRecord newRecord :
                storage.getRecordsForValue(UserKeyRecord.META_KEY_STATUS, UserKeyRecord.TYPE_NEW)) {

            // See if we have another sandbox with this ID that is fully initialized.
            if (storage.getIDsForValues(
                    new String[]{UserKeyRecord.META_SANDBOX_ID, UserKeyRecord.META_KEY_STATUS},
                    new Object[]{newRecord.getUuid(), UserKeyRecord.TYPE_NORMAL}
            ).size() > 0) {
                Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Marking new sandbox " + newRecord.getUuid() + " as initialized, since it's already in use on this device");
                // If so, this sandbox _has_ to have already been initialized, and we should treat it as such.
                newRecord.setType(UserKeyRecord.TYPE_NORMAL);
                storage.write(newRecord);
            }


        for (UserKeyRecord recordPendingDelete :
                storage.getRecordsForValue(UserKeyRecord.META_KEY_STATUS, UserKeyRecord.TYPE_PENDING_DELETE)) {
                Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Cleaning up sandbox which is pending removal");
                
                // See if there are more records in this sandbox. (If so, we can just wipe this record and move on)
                if (storage.getIDsForValue(UserKeyRecord.META_SANDBOX_ID, recordPendingDelete.getUuid()).size() > 2) {
                    Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Record for sandbox " + recordPendingDelete.getUuid() + " has siblings. Removing record");
                    
                    //TODO: Will this invalidate our iterator?
                    storage.remove(recordPendingDelete);
                } else {
                    // Otherwise, we should see if we can read the data, and if so, wipe it as well as the record.
                    if (recordPendingDelete.isPasswordValid(password)) {
                        //TODO AMS: Changed this such that you can only wipe the record if it was a password login -- is that OK?
                        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Current user has access to purgable sandbox " + recordPendingDelete.getUuid() + ". Wiping that sandbox");
                        UserSandboxUtils.purgeSandbox(this.getContext(), app, recordPendingDelete, recordPendingDelete.unWrapKey(password));
                    }
                    //Do we do anything here if we couldn't open the sandbox?
                }
            }
            //TODO: Specifically we should never have two sandboxes which can be opened by the same password (I think...)
        }
    }
    
    
    
    //CTS: These will be fleshed out to comply with the server's Key Request/response protocol

    @Override
    protected HttpResponse doHttpRequest() throws ClientProtocolException, IOException {
        HttpRequestGenerator requestor = new HttpRequestGenerator(username, password);
        return requestor.makeKeyFetchRequest(keyServerUrl, null);
    }

    @Override
    protected TransactionParserFactory getTransactionParserFactory() {
        return new TransactionParserFactory() {

            @Override
            public TransactionParser getParser(KXmlParser parser) {
                String name = parser.getName();
                if("auth_keys".equals(name)) {
                    return new KeyRecordParser(parser, username, password) {

                        @Override
                        public void commit(ArrayList<UserKeyRecord> parsed) throws IOException {
                            ManageKeyRecordTask.this.keyRecords = parsed;
                        }

                    };
                } else {
                    return null;
                }
            }
        };
    }
    
    @Override
    protected boolean shouldMakeHttpCallout() {
        return calloutNeeded;
    }

    @Override
    protected boolean calloutSuccessRequired() {
        return calloutSuccessRequired;
    }

    @Override
    protected boolean processSuccessfulRequest() {
        if (keyRecords == null || keyRecords.size() == 0) {
            Logger.log(AndroidLogger.TYPE_USER, "No key records received on server request!");
            return false;
        }
        
        Logger.log(AndroidLogger.TYPE_USER, "Key record request complete. Received: " + keyRecords.size() + " key records from server");
        SqlStorage<UserKeyRecord> storage = app.getStorage(UserKeyRecord.class);
        try {
            //We successfully received and parsed out some key records! Let's update the db
            for (UserKeyRecord record : keyRecords) {

                // See if we already have a key record for this sandbox and user
                // (There should _definitely_ only be one if there is one)
                UserKeyRecord existing;

                try {
                    existing = storage.getRecordForValues(
                            new String[]{UserKeyRecord.META_SANDBOX_ID, UserKeyRecord.META_USERNAME},
                            new Object[] {record.getUuid(), record.getUsername()});
                    
                    Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Got new record for existing sandbox " + existing.getUuid() + " . Merging");
                    //So we have an existing record. Either we're updating our current record and we're updating the key details
                    //or our password has changed and we need to overwrite the existing key record. Either way, all
                    //we should need to do is merge the records.
                    
                    UserKeyRecord ukr = new UserKeyRecord(
                            record.getUsername(), record.getPasswordHash(),
                            record.getEncryptedKey(), record.getWrappedPassword(),
                            record.getValidFrom(), record.getValidTo(), record.getUuid(),
                            existing.getType());
                    ukr.setID(existing.getID());
                    storage.write(ukr);
                } catch(NoSuchElementException nsee) {
                    // If there's no existing record, write this new one (we'll handle updating the status later)
                    storage.write(record);
                }
            }
        } catch (StorageFullException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    @Override
    protected HttpCalloutTask.HttpCalloutOutcomes doPostCalloutTask(boolean calloutFailed) {
        // First, check for consistency in our key records
        cleanupUserKeyRecords();

        UserKeyRecord current = getCurrentValidRecord();

        if (current == null)  {
            if (inPinMode) {
                // If we are in pin mode then we did not execute the callout task; just means there
                // is no existing record matching the username/pin combo
                return HttpCalloutOutcomes.IncorrectPin;
            } else {
                return HttpCalloutOutcomes.UnknownError;
            }
        }

        if (inPinMode) {
            // If we successfully found a matching record in pin mode, we are now going to need
            // access to the un-hashed password to finish up
            this.password = current.getUnhashedPasswordViaPin(this.pin);
        }

        // Now, see if we need to do anything to process our new record.
        if (current.getType() != UserKeyRecord.TYPE_NORMAL) {
            if (current.getType() == UserKeyRecord.TYPE_NEW) {
                // See if we can migrate an old sandbox's data to the new sandbox.
                if (!lookForAndMigrateOldSandbox(current)) {
                    // TODO: Problem during migration! Should potentially try again instead of leaving old one

                    // Switching over to using the old record instead of failing
                    current = getInUserSandbox(current.getUsername(), app.getStorage(UserKeyRecord.class));

                    // Make sure we didn't somehow not get a new sandbox
                    if(current == null ){ 
                        Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                                "Somehow we both failed to migrate an old DB and also didn't _havE_ an old db");
                        return HttpCalloutTask.HttpCalloutOutcomes.UnknownError;
                    }
                    
                    // Otherwise we're now keyed up with the old DB and we should be fine to log in
                }
            } else if (current.getType() == UserKeyRecord.TYPE_LEGACY_TRANSITION) {
                // Transition the legacy storage to the new format. We don't have a new record,
                // so don't worry
                try {
                    this.publishProgress(Localization.get("key.manage.legacy.begin"));
                    LegacyInstallUtils.transitionLegacyUserStorage(getContext(), CommCareApplication._().getCurrentApp(), current.unWrapKey(password), current);
                } catch(Exception e) {
                    e.printStackTrace();
                    // Ugh, high level trap catch
                    // Problem during migration! We should try again? Maybe?
                    // Or just leave the old one?
                    Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Error while trying to migrate legacy database! Exception: " + e.getMessage());
                    // For now, fail.
                    return HttpCalloutTask.HttpCalloutOutcomes.UnknownError;
                }
            }
        }
        
        //Ok, so we're done with everything now. We should log in our local sandbox and proceed to the next step.
        CommCareApplication._().startUserSession(current.unWrapKey(password), current, restoreSession);
        
        //So we may have logged in a key record but not a user (if we just received the
        //key, but not the user's data, for instance). 
        try {
            User u = CommCareApplication._().getSession().getLoggedInUser();
            if(u != null) {
                u.setCachedPwd(password);
                loggedIn = u;
            }
        } catch(SessionUnavailableException sue) {
            
        }
        
        return HttpCalloutTask.HttpCalloutOutcomes.Success;
    }
    
    private UserKeyRecord getInUserSandbox(String username, SqlStorage<UserKeyRecord> storage) {
        UserKeyRecord oldSandboxToMigrate = null;
        
        for (UserKeyRecord ukr : storage.getRecordsForValue(UserKeyRecord.META_USERNAME, username)) {
            if (ukr.getType() == UserKeyRecord.TYPE_NEW) {
                // This record is also new (which is kind of sketchy) so it's not helpful
                continue;
            }
            
            // Ok, so we have an old record that's been in use for this user. See if this password is the same
            if (!ukr.isPasswordValid(password)) {
                //Otherwise, this was saved for a different password. We would have simply overwritten the record
                //if our sandboxes matched, so we can't do anything with it.
                continue;
            }
            
            // Ok, so only one more question: We may have migrated a sandbox in the past already,
            // so we should only overwrite this record if it's the newest (although it's a bad sign
            // if we have two existing sandboxes which can be unlocked with the same password)
            if (oldSandboxToMigrate == null || ukr.getValidFrom().after(oldSandboxToMigrate.getValidFrom())) {
                oldSandboxToMigrate = ukr;
            } else {
                Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Two old sandboxes exist with the same username");
            }
        }
        
        return oldSandboxToMigrate;
    }

    //TODO: This can be its own method/process somewhere
    private boolean lookForAndMigrateOldSandbox(UserKeyRecord newRecord) {
        //So we have a new record here. We want to look through our old records now and see if we can
        //(A) Migrate over any of their old data to this new sandbox.
        //(B) Wipe that old record once the migrated record is completed (and see if we should wipe the 
        //sandbox's data).
        SqlStorage<UserKeyRecord> storage = app.getStorage(UserKeyRecord.class);

        UserKeyRecord oldSandboxToMigrate = getInUserSandbox(newRecord.getUsername(), storage);

        //Our new record is completely new. Easy and awesome. Record and move on.
        if(oldSandboxToMigrate == null) {
            newRecord.setType(UserKeyRecord.TYPE_NORMAL);
            storage.write(newRecord);
            //No worries
            return true;
        }
        
        //Otherwise we should start migrating that data over.
        byte[] oldKey = oldSandboxToMigrate.unWrapKey(password);
        
        //First see if the old sandbox is legacy and needs to be transfered over.
        if(oldSandboxToMigrate.getType() == UserKeyRecord.TYPE_LEGACY_TRANSITION) {
            //transition the old storage into the new format before we copy the DB over.
            LegacyInstallUtils.transitionLegacyUserStorage(getContext(), CommCareApplication._().getCurrentApp(), oldKey, oldSandboxToMigrate);
            publishProgress(Localization.get("key.manage.legacy.begin"));
        }

        //TODO: Ok, so what error handling do we need here? 
        try {
            //Otherwise we need to copy the old sandbox to a new location atomically (in case we fail).
            UserSandboxUtils.migrateData(this.getContext(), app, oldSandboxToMigrate, oldKey, newRecord, CryptUtil.unWrapKey(newRecord.getEncryptedKey(), password));
            publishProgress(Localization.get("key.manage.migrate"));
            return true;
        } catch(IOException ioe) {
            ioe.printStackTrace();
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "IO Error while migrating database: " + ioe.getMessage());
            return false;
        } catch(Exception e) {
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Unexpected error while migrating database: " + ExceptionReporting.getStackTrace(e));
            return false;
        }
    }

    @Override
    protected HttpCalloutOutcomes doResponseOther(HttpResponse response) {
        return HttpCalloutOutcomes.BadResponse;
    }

    // XXX PLM: getCurrentValidRecord is called w/ acceptExpired set to
    // true. Eventually we will enforce user key record expiration, but
    // can't do so until we proactively refresh records that are going to
    // expire in the next few months. Otherwise, devices that haven't
    // accessed the internet in a while won't be able to perform logins.
    private UserKeyRecord getCurrentValidRecord() {
        if (inPinMode) {
            return UserKeyRecord.getCurrentValidRecordByPin(app, username, pin, true);
        } else {
            return UserKeyRecord.getCurrentValidRecordByPassword(app, username, password, true);
        }
    }
}
