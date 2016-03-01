package org.commcare.android.db.legacy;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;

import org.commcare.android.crypt.CipherPool;
import org.commcare.android.database.AndroidDbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.ResourceModelUpdater;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.DatabaseUserOpenHelper;
import org.commcare.android.database.user.UserSandboxUtils;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.GeocodeCacheModel;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.logging.AndroidLogEntry;
import org.commcare.android.logging.AndroidLogger;
import org.commcare.android.logging.DeviceReportRecord;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.FormsProviderAPI;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.resources.model.Resource;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.model.User;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.PropertyUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;


/**
 * @author ctsims
 */
public class LegacyInstallUtils {

    public static final String LEGACY_UPGRADE_PROGRESS = "legacy_upgrade_progress";
    public static final String UPGRADE_COMPLETE = "complete";

    public static void checkForLegacyInstall(Context c, SqlStorage<ApplicationRecord> currentAppStorage) throws SessionUnavailableException {
        SharedPreferences globalPreferences = PreferenceManager.getDefaultSharedPreferences(c);
        if (globalPreferences.getString(LEGACY_UPGRADE_PROGRESS, "").equals(UPGRADE_COMPLETE)) {
            return;
        }
        //Check to see if the legacy database exists on this system
        if (!c.getDatabasePath(GlobalConstants.CC_DB_NAME).exists()) {
            globalPreferences.edit().putString(LEGACY_UPGRADE_PROGRESS, UPGRADE_COMPLETE).commit();
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "No legacy installs detected. Skipping transition");
            return;
        }

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| DB Detected");

        //There is a legacy DB. First, check whether we've already moved things over (whether a new
        //app is already installed)
        int installedApps = currentAppStorage.getNumRecords();

        ApplicationRecord record = null;
        if (installedApps == 0) {
            //there are no installed apps
        } else if (installedApps == 1) {
            for (ApplicationRecord r : currentAppStorage) {
                int status = r.getStatus();
                if (status == ApplicationRecord.STATUS_SPECIAL_LEGACY) {
                    //We've already started this process, but it didn't
                    //finish
                    record = r;
                }
            }

            //if the application present is either uninitialized or already
            //installed, we don't need to proceed.
            if (record == null) {
                globalPreferences.edit().putString(LEGACY_UPGRADE_PROGRESS, UPGRADE_COMPLETE).commit();
                Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| app was detected, but new install already covers it");
                return;
            }

        } else {
            //there's more than one app installed, which means we 
            //must have passed through here.
            globalPreferences.edit().putString(LEGACY_UPGRADE_PROGRESS, UPGRADE_COMPLETE).commit();
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| More than one app record installed, skipping legacy app detection");
            return;
        }

        //Ok, so now we have an old db, and either an incomplete app install, or
        //no app install.

        //Next step, determine if there was an old, completely installed app in the old db.
        //If this isn't true, we can just bail.


        //get the legacy storage
        final android.database.sqlite.SQLiteDatabase olddb = new LegacyCommCareOpenHelper(c).getReadableDatabase();
        LegacyDbHelper ldbh = new LegacyDbHelper(c) {
            @Override
            public android.database.sqlite.SQLiteDatabase getHandle() {
                return olddb;
            }
        };

        //Ok, so now we need to see whether there's app on the legacy db.
        //Use the name of the Pre-DB3 Global Resource table (in case it has changed).
        //Note: ResourceModelUpdater is mandatory here to read pre-db3 resource records.
        LegacySqlIndexedStorageUtility<Resource> legacyResources = new LegacySqlIndexedStorageUtility<Resource>("GLOBAL_RESOURCE_TABLE", ResourceModelUpdater.class, ldbh);

        //see if the resource table is installed
        boolean hasProfile = false;
        boolean allInstalled = true;
        int oldDbSize = 0;
        for (Resource r : legacyResources) {
            if (r.getStatus() != Resource.RESOURCE_STATUS_INSTALLED) {
                allInstalled = false;
            }
            if (r.getResourceId().equals(CommCarePlatform.APP_PROFILE_RESOURCE_ID)) {
                hasProfile = true;
            }
            oldDbSize++;
        }

        if (hasProfile && allInstalled) {
            //Legacy application is installed and ready to transition
        } else {
            globalPreferences.edit().putString(LEGACY_UPGRADE_PROGRESS, UPGRADE_COMPLETE).commit();
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy app detected, but it wasn't fully installed");
            return;
        }

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| application installed. Beginning transition");

        //So if we've gotten this far, we definitely have an app we need to copy over. See if we have an application record, and create
        //one if not.

        if (record == null) {
            record = new ApplicationRecord("legacy_application", ApplicationRecord.STATUS_SPECIAL_LEGACY);
        }
        //Commit? We can skip the searching for the old app if so, maybe?

        //Ok, so fire up a seat for the new Application
        CommCareApp app = new CommCareApp(record);

        //Don't, however, create any of the file roots, we need to keep that namespace
        //clean to copy over files.
        app.setupSandbox(false);

        //Ok, so. We now have a valid application record and can start moving over records.
        //App data used to exist in three places, so we'll copy over all three
        //1) DB Records
        //2) File System Data
        //3) Update File system references
        //4) Application settings
        //5) Stubbed out user keys

        //1) DB Records
        //   The following models need to be moved: Resource Table entries, fixtures, and logs
        SqlStorage<Resource> newInstallTable = app.getStorage("GLOBAL_RESOURCE_TABLE", Resource.class);
        SqlStorage.cleanCopy(legacyResources, newInstallTable);

        //Fixtures
        LegacySqlIndexedStorageUtility<FormInstance> legacyFixtures = new LegacySqlIndexedStorageUtility<>("fixture", FormInstance.class, ldbh);
        SqlStorage<FormInstance> newFixtures = app.getStorage("fixture", FormInstance.class);
        SqlStorage.cleanCopy(legacyFixtures, newFixtures);

        //Logs

        //There's a twist, here. We only wanna copy over logs once to get a nice clear time-based record ordering.
        LegacySqlIndexedStorageUtility<AndroidLogEntry> legacyLogs = new LegacySqlIndexedStorageUtility<>(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class, ldbh);

        if (legacyLogs.isEmpty()) {
            //old logs are empty, no need to wipe new storage 
        } else {
            SqlStorage<AndroidLogEntry> newLogs = CommCareApplication._().getGlobalStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class);
            SqlStorage.cleanCopy(legacyLogs, newLogs);

            //logs are copied over, wipe the old ones.
            legacyLogs.removeAll();
        }


        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| Global resources copied");
        //TODO: Record Progress?


        //2) Ok, so now we want to copy over any old file system storage. Any jr:// prefixes should work fine after this move, 
        //but form records and such will still need to be updated. Unfortunately if people have placed their media in jr://file
        //instead of jr://file/commcare we're going to miss it here, which sucks, but otherwise we run the risk of breaking future
        //installs

        //We supressed the file system generation, so make sure the root folder exists 
        new File(app.storageRoot()).mkdirs();

        //Copy over the old file root
        File oldRoot = new File(getOldFileSystemRoot());
        String newRoot = app.fsPath("commcare/");
        oldRoot.renameTo(new File(newRoot));

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| Files moved. Updating Handles");

        //We also need to tell the XForm Provider that any/all of its forms have been moved

        ArrayList<Pair<Uri, String>> toReplace = new ArrayList<>();
        Cursor ef = null;
        try {
            ef = c.getContentResolver().query(FormsProviderAPI.FormsColumns.CONTENT_URI, new String[]{FormsProviderAPI.FormsColumns.FORM_FILE_PATH, FormsProviderAPI.FormsColumns._ID}, null, null, null);
            if (ef != null) {
                while (ef.moveToNext()) {
                    String filePath = ef.getString(ef.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH));
                    String newFilePath = replaceOldRoot(filePath, getOldFileSystemRoot(), newRoot);
                    if (!newFilePath.equals(filePath)) {
                        Uri uri = ContentUris.withAppendedId(FormsProviderAPI.FormsColumns.CONTENT_URI, ef.getLong(ef.getColumnIndex(FormsProviderAPI.FormsColumns._ID)));
                        toReplace.add(new Pair<>(uri, newFilePath));
                    }
                }
            }
        } finally {
            if (ef != null) {
                ef.close();
            }
        }

        for (Pair<Uri, String> p : toReplace) {
            ContentValues cv = new ContentValues();
            cv.put(FormsProviderAPI.FormsColumns.FORM_FILE_PATH, p.second);
            int updated = c.getContentResolver().update(p.first, cv, null, null);
            if (updated != 1) {
                Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| Warning: wrong number of xform content URI's updated: " + updated);

            }
        }

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| " + toReplace.size() + " xform content file paths updated. Moving prefs");

        //3) Ok, so now we have app settings to copy over. Basically everything in the SharedPreferences should get put in the new app
        //preferences

        Map<String, ?> oldPrefs = globalPreferences.getAll();
        //well, this sucks more than anticipated.
        Editor e = app.getAppPreferences().edit();
        for (String k : oldPrefs.keySet()) {
            Object o = oldPrefs.get(k);
            if (o instanceof String) {
                e.putString(k, (String)o);
            } else if (o instanceof Integer) {
                e.putInt(k, (Integer)o);
            } else if (o instanceof Long) {
                e.putLong(k, (Long)o);
            } else if (o instanceof Boolean) {
                e.putBoolean(k, (Boolean)o);
            } else if (o instanceof Float) {
                e.putFloat(k, (Float)o);
            }
        }
        e.commit();

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| Prefs updated. Adding user records...");
        //4) Finally, we need to register a new UserKeyRecord which will prepare the user-facing records for transition
        //when the user logs in again

        //Get legacy user storage
        LegacySqlIndexedStorageUtility<User> legacyUsers = new LegacySqlIndexedStorageUtility<>("USER", User.class, ldbh);

        ArrayList<User> oldUsers = new ArrayList<>();
        //So the old user storage wasn't encrypted since it had no actual prod data in it
        for (User u : legacyUsers) {
            oldUsers.add(u);
        }
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| " + oldUsers.size() + " old user records detected");

        //we're done with the old storage now.
        olddb.close();

        SqlStorage<UserKeyRecord> newUserKeyRecords = app.getStorage(UserKeyRecord.class);

        User preferred = null;
        //go through all of the old users and generate key records for them
        for (User u : oldUsers) {
            //make sure we haven't already handled this user somehow
            if (newUserKeyRecords.getIDsForValue(UserKeyRecord.META_USERNAME, u.getUsername()).size() > 0) {
                Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| Skipping key record for " + u.getUsername() + " . One already exists?");
                continue;
            }

            //See if we can find a user who was the last to log in
            if (preferred == null || u.getUsername().toLowerCase().equals(globalPreferences.getString(CommCarePreferences.LAST_LOGGED_IN_USER, "").toLowerCase())) {
                preferred = u;
            }

            //There's not specific reason to thing this might happen, but might be valuable to double check
            if (newUserKeyRecords.getIDsForValue(UserKeyRecord.META_USERNAME, u.getUsername()).size() == 0) {
                String sandboxId = PropertyUtils.genUUID().replace("-", "");
                UserKeyRecord ukr = new UserKeyRecord(u.getUsername(), u.getPasswordHash(),
                        u.getWrappedKey(), new Date(), new Date(), sandboxId,
                        UserKeyRecord.TYPE_LEGACY_TRANSITION);
                newUserKeyRecords.write(ukr);
            }
        }

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| App succesfully Transitioned! Writing progress");

        //First off: All of the app resources are now transitioned. We can continue to handle data transitions at login if the following fails
        app.writeInstalled();
        globalPreferences.edit().putString(LEGACY_UPGRADE_PROGRESS, UPGRADE_COMPLETE).commit();

        //Trigger all new resends
        app.getAppPreferences().edit().putLong(CommCarePreferences.LOG_LAST_DAILY_SUBMIT, 0).commit();

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Legacy| App labelled. Attempting user transition");

        //Now, we should try to transition over legacy user storage if any of the previous users are on test data

        //First, see if the preferred user (Only one if available, or the most recent login)
        if (preferred != null) {
            //There should only be one user key record for that user which is legacy
            for (UserKeyRecord ukr : newUserKeyRecords.getRecordsForValues(new String[]{UserKeyRecord.META_USERNAME}, new String[]{preferred.getUsername()})) {
                if (ukr.getType() == UserKeyRecord.TYPE_LEGACY_TRANSITION) {
                    try {
                        transitionLegacyUserStorage(c, app, generateOldTestKey(c).getEncoded(), ukr);
                    } catch (RuntimeException re) {
                        //expected if they used a real key
                        re.printStackTrace();
                    }
                    break;
                }
            }
        }
        String toSkip = preferred == null ? null : preferred.getUsername();
        for (UserKeyRecord ukr : newUserKeyRecords) {
            if (ukr.getUsername().equals(toSkip)) {
                continue;
            }
            if (ukr.getType() == UserKeyRecord.TYPE_LEGACY_TRANSITION) {
                try {
                    transitionLegacyUserStorage(c, app, generateOldTestKey(c).getEncoded(), ukr);
                } catch (RuntimeException re) {
                    //expected if they used a real key
                    re.printStackTrace();
                }
                break;
            }
        }

        //Okay! Whew. We're now all set. Anything else that needs to happen should happen when the user logs in and we
        //can unwrap their keys
        app.teardownSandbox();
    }

    private static String getOldFileSystemRoot() {
        String filesystemHome = CommCareApplication._().getAndroidFsRoot();
        return filesystemHome + "commcare/";
    }

    public static void transitionLegacyUserStorage(final Context c, CommCareApp app, final byte[] oldKey, UserKeyRecord ukr) {
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Beginning transition attempt for " + ukr.getUsername());

        final CipherPool pool = new CipherPool() {
            final Object lock = new Object();
            final byte[] key = oldKey;

            @Override
            public Cipher generateNewCipher() {
                synchronized (lock) {
                    try {
                        synchronized (key) {
                            SecretKeySpec spec = new SecretKeySpec(key, "AES");
                            Cipher decrypter = Cipher.getInstance("AES");
                            decrypter.init(Cipher.DECRYPT_MODE, spec);

                            return decrypter;
                        }
                    } catch (InvalidKeyException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (NoSuchAlgorithmException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (NoSuchPaddingException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                return null;
            }

        };


        //get the legacy storage
        final android.database.sqlite.SQLiteDatabase olddb = new LegacyCommCareOpenHelper(c, new LegacyCommCareDBCursorFactory(getLegacyEncryptedModels()) {
            protected CipherPool getCipherPool() {
                return pool;
            }
        }).getReadableDatabase();

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Legacy DB Opened");

        LegacyDbHelper ldbh = new LegacyDbHelper(c, pool.borrow()) {
            @Override
            public android.database.sqlite.SQLiteDatabase getHandle() {
                return olddb;
            }
        };

        final String newFileSystemRoot = app.fsPath("commcare/");
        final String oldRoot = getOldFileSystemRoot();

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Testing keys by attempting to open storage");

        //TODO: This doesn't work.
        LegacySqlIndexedStorageUtility<User> legacyUserStorage = new LegacySqlIndexedStorageUtility<>("User", User.class, ldbh);
        try {
            //Test to see if the old db worked
            for (User u : legacyUserStorage) {

            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Exception " + e.getMessage() + " when testing storage. Keys are probably no good");
            //This almost certainly means that we don't have the right key;
            return;
        }

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Old keys look good! Creating new DB");

        SQLiteDatabase ourDb;
        //If we were able to iterate over the users, the key was fine, so let's use it to open our db
        try {
            ourDb = new DatabaseUserOpenHelper(CommCareApplication._(), ukr.getUuid()).getWritableDatabase(UserSandboxUtils.getSqlCipherEncodedKey(oldKey));
        } catch (SQLiteException sle) {
            //Our database got corrupted. Fortunately this represents a new record, so we can't actually need it.
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Attempted migrated database got corrupted. Deleting it and starting over");
            c.getDatabasePath(DatabaseUserOpenHelper.getDbName(ukr.getUuid())).delete();
            ourDb = new DatabaseUserOpenHelper(CommCareApplication._(), ukr.getUuid()).getWritableDatabase(UserSandboxUtils.getSqlCipherEncodedKey(oldKey));
        }

        final SQLiteDatabase currentUserDatabase = ourDb;

        AndroidDbHelper newAndroidDbHelper = new AndroidDbHelper(c) {
            @Override
            public SQLiteDatabase getHandle() {
                return currentUserDatabase;
            }
        };

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| All set to get going. Beginning storage copy");


        try {
            //So we need to copy over a bunch of storage and also make some incidental changes along the way.

            LegacySqlIndexedStorageUtility<ACase> legacyCases = new LegacySqlIndexedStorageUtility<>(ACase.STORAGE_KEY, ACase.class, ldbh);
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| " + legacyCases.getNumRecords() + " old cases detected");

            Map m = SqlStorage.cleanCopy(legacyCases,
                    new SqlStorage<>(ACase.STORAGE_KEY, ACase.class, newAndroidDbHelper));

            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| " + m.size() + " cases copied. Copying Users");

            SqlStorage.cleanCopy(legacyUserStorage,
                    new SqlStorage<>("USER", User.class, newAndroidDbHelper));

            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Users copied. Copying form records");

            final Map<Integer, Integer> formRecordMapping = SqlStorage.cleanCopy(new LegacySqlIndexedStorageUtility<>("FORMRECORDS", FormRecord.class, ldbh),
                    new SqlStorage<>("FORMRECORDS", FormRecord.class, newAndroidDbHelper), new CopyMapper<FormRecord>() {

                        @Override
                        public FormRecord transform(FormRecord t) {
                            String formRecordPath;
                            try {
                                formRecordPath = t.getPath(c);
                                String newPath = replaceOldRoot(formRecordPath, oldRoot, newFileSystemRoot);
                                if (!newPath.equals(formRecordPath)) {
                                    ContentValues cv = new ContentValues();
                                    cv.put(InstanceColumns.INSTANCE_FILE_PATH, newPath);
                                    c.getContentResolver().update(t.getInstanceURI(), cv, null, null);
                                }
                                return t;
                            } catch (FileNotFoundException e) {
                                //This means the form record doesn't
                                //actually have a URI at all, so we
                                //can skip this.
                                return t;
                            }
                        }

                    });

            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Form records copied. Copying sessions.");

            SqlStorage.cleanCopy(new LegacySqlIndexedStorageUtility<>("android_cc_session", SessionStateDescriptor.class, ldbh),
                    new SqlStorage<>("android_cc_session", SessionStateDescriptor.class, newAndroidDbHelper), new CopyMapper<SessionStateDescriptor>() {

                        @Override
                        public SessionStateDescriptor transform(SessionStateDescriptor t) {
                            return t.reMapFormRecordId(formRecordMapping.get(t.getFormRecordId()));
                        }

                    });
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Sessions copied. Copying geocaches.");

            SqlStorage.cleanCopy(new LegacySqlIndexedStorageUtility<>(GeocodeCacheModel.STORAGE_KEY, GeocodeCacheModel.class, ldbh),
                    new SqlStorage<>(GeocodeCacheModel.STORAGE_KEY, GeocodeCacheModel.class, newAndroidDbHelper));

            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| geocaches copied. Copying serialized log submissions.");

            SqlStorage.cleanCopy(new LegacySqlIndexedStorageUtility<>("log_records", DeviceReportRecord.class, ldbh),
                    new SqlStorage<>("log_records", DeviceReportRecord.class, newAndroidDbHelper), new CopyMapper<DeviceReportRecord>() {
                        @Override
                        public DeviceReportRecord transform(DeviceReportRecord t) {
                            return new DeviceReportRecord(replaceOldRoot(t.getFilePath(), oldRoot, newFileSystemRoot), t.getKey());
                        }

                    });

            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| serialized log submissions copied. Copying fixtures");

            SqlStorage.cleanCopy(new LegacySqlIndexedStorageUtility<>("fixture", FormInstance.class, ldbh),
                    new SqlStorage<>("fixture", FormInstance.class, newAndroidDbHelper));

        } catch (SessionUnavailableException sfe) {
            throw new RuntimeException(sfe);
        }

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Whew, storage copied! Updating key record");

        //Now we can update this key record to confirm that it is fully installed
        ukr.setType(UserKeyRecord.TYPE_NORMAL);
        app.getStorage(UserKeyRecord.class).write(ukr);

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| Eliminating shared data from old install, since new users can't access it");

        //Now, if we've copied everything over to this user with no problems, we want to actually go back and wipe out all of the
        //data that is linked to specific files, since individual users might delete them out of their sandboxes.
        new LegacySqlIndexedStorageUtility<>("log_records", DeviceReportRecord.class, ldbh).removeAll();
        new LegacySqlIndexedStorageUtility<>(FormRecord.STORAGE_KEY, FormRecord.class, ldbh).removeAll();
        new LegacySqlIndexedStorageUtility<>("android_cc_session", SessionStateDescriptor.class, ldbh).removeAll();

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "LegacyUser| User transitioned! Closing db handles.");

        olddb.close();
        currentUserDatabase.close();
    }

    protected static String replaceOldRoot(String filePath, String oldRoot, String newFileSystemRoot) {
        try {
            //TODO: It's really bad if these don't cannonicalize the same.
            oldRoot = new File(oldRoot).getCanonicalPath();
            filePath = new File(filePath).getCanonicalPath();
            if (filePath.contains(oldRoot)) {
                return filePath.replace(oldRoot, newFileSystemRoot);
            }
            return filePath;
        } catch (IOException ioe) {
            //This shouldn't happen and kind of sucks if it does. Should only occur if the file paths are invalid
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Couldn't cannonicalize " + oldRoot + " or " + filePath);
            return filePath;
        }
    }

    private static Hashtable<String, EncryptedModel> getLegacyEncryptedModels() {
        Hashtable<String, EncryptedModel> models = new Hashtable<>();
        models.put(ACase.STORAGE_KEY, new ACase());
        models.put("FORMRECORDS", new FormRecord());
        models.put(GeocodeCacheModel.STORAGE_KEY, new GeocodeCacheModel());
        models.put("log_records", new DeviceReportRecord());
        return models;
    }

    public interface CopyMapper<T extends Persistable> {
        T transform(T t);
    }

    private static SecretKeySpec generateOldTestKey(Context c) {
        KeyGenerator generator;
        try {
            generator = KeyGenerator.getInstance("AES");
            generator.init(256, new SecureRandom(getPhoneIdOld(c).getBytes()));
            return new SecretKeySpec(generator.generateKey().getEncoded(), "AES");
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private static String getPhoneIdOld(Context c) {
        TelephonyManager manager = (TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = manager.getDeviceId();
        if (imei == null) {
            imei = Secure.ANDROID_ID;
        }
        return imei;
    }
}
