package org.commcare.tasks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.conn.ConnectTimeoutException;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.engine.cases.CaseUtils;
import org.commcare.logging.AndroidLogger;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.app.models.UserKeyRecord;
import org.commcare.models.database.user.models.ACase;
import org.commcare.models.encryption.CryptUtil;
import org.commcare.modern.models.RecordTooLargeException;
import org.commcare.network.DataPullRequester;
import org.commcare.network.DataPullResponseFactory;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.network.RemoteDataPullResponse;
import org.commcare.resources.model.CommCareOTARestoreListener;
import org.commcare.services.CommCareSessionService;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.FormSaveUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.bitcache.BitCache;
import org.commcare.xml.AndroidTransactionParserFactory;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Hashtable;
import java.util.NoSuchElementException;

import javax.crypto.SecretKey;

/**
 * @author ctsims
 */
public abstract class DataPullTask<R> extends CommCareTask<Void, Integer, DataPullTask.PullTaskResult, R>
        implements CommCareOTARestoreListener {
    private final String server;
    private final String username;
    private final String password;
    private final Context context;

    private int mCurrentProgress = -1;
    private int mTotalItems = -1;
    private long mSyncStartTime;

    private boolean wasKeyLoggedIn;
    private final boolean restoreSession;

    public static final int DATA_PULL_TASK_ID = 10;

    public static final int PROGRESS_STARTED = 0;
    public static final int PROGRESS_CLEANED = 1;
    public static final int PROGRESS_AUTHED = 2;
    private static final int PROGRESS_DONE = 4;
    public static final int PROGRESS_RECOVERY_NEEDED = 8;
    public static final int PROGRESS_RECOVERY_STARTED = 16;
    private static final int PROGRESS_RECOVERY_FAIL_SAFE = 32;
    private static final int PROGRESS_RECOVERY_FAIL_BAD = 64;
    public static final int PROGRESS_PROCESSING = 128;
    public static final int PROGRESS_DOWNLOADING = 256;
    private DataPullRequester dataPullRequester;

    private DataPullTask(String username, String password, String server, Context context, boolean restoreOldSession) {
        this.server = server;
        this.username = username;
        this.password = password;
        this.context = context;
        this.taskId = DATA_PULL_TASK_ID;
        this.dataPullRequester = new DataPullResponseFactory();
        this.restoreSession = restoreOldSession;

        TAG = DataPullTask.class.getSimpleName();
    }

    public DataPullTask(String username, String password, String server, Context context) {
        this(username, password, server, context, false);
    }

    private DataPullTask(String username, String password, String server, Context context, DataPullRequester dataPullRequester) {
        this(username, password, server, context);
        this.dataPullRequester = dataPullRequester;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        if (wasKeyLoggedIn) {
            CommCareApplication._().releaseUserResourcesAndServices();
        }
    }

    @Override
    protected PullTaskResult doTaskBackground(Void... params) {
        // Don't try to sync if logging out is occuring
        if (!CommCareSessionService.sessionAliveLock.tryLock()) {
            // TODO PLM: once this task is refactored into manageable
            // components, it should use the ManagedAsyncTask pattern of
            // checking for isCancelled() and aborting at safe places.
            return PullTaskResult.UNKNOWN_FAILURE;
        }


        // Wrap in a 'try' to enable a 'finally' close that releases the
        // sessionAliveLock.
        try {
            publishProgress(PROGRESS_STARTED);
            CommCareApp app = CommCareApplication._().getCurrentApp();
            SharedPreferences prefs = app.getAppPreferences();

            String keyServer = prefs.getString("key_server", null);

            mTotalItems = -1;
            mCurrentProgress = -1;

            //Whether or not we should be generating the first key
            boolean useExternalKeys = !(keyServer == null || keyServer.equals(""));

            boolean loginNeeded = true;
            boolean useRequestFlags = false;
            try {
                loginNeeded = !CommCareApplication._().getSession().isActive();
            } catch (SessionUnavailableException sue) {
                //expected if we aren't initialized.
            }

            PullTaskResult responseError = PullTaskResult.UNKNOWN_FAILURE;

            //This should be per _user_, not per app
            prefs.edit().putLong("last-ota-restore", new Date().getTime()).commit();

            HttpRequestGenerator requestor = new HttpRequestGenerator(username, password);

            AndroidTransactionParserFactory factory = new AndroidTransactionParserFactory(context, requestor) {
                boolean publishedAuth = false;

                @Override
                public void reportProgress(int progress) {
                    if (!publishedAuth) {
                        DataPullTask.this.publishProgress(PROGRESS_AUTHED, progress);
                        publishedAuth = true;
                    }
                }
            };
            Logger.log(AndroidLogger.TYPE_USER, "Starting Sync");
            long bytesRead = -1;

            UserKeyRecord ukr = null;

            try {
                // This is a dangerous way to do this (the null settings), should revisit later
                if (loginNeeded) {
                    if (!useExternalKeys) {
                        // Get the key
                        SecretKey newKey = CryptUtil.generateSemiRandomKey();

                        if (newKey == null) {
                            this.publishProgress(PROGRESS_DONE);
                            return PullTaskResult.UNKNOWN_FAILURE;
                        }
                        String sandboxId = PropertyUtils.genUUID().replace("-", "");
                        ukr = new UserKeyRecord(username, UserKeyRecord.generatePwdHash(password),
                                CryptUtil.wrapByteArrayWithString(newKey.getEncoded(), password),
                                new Date(), new Date(Long.MAX_VALUE), sandboxId);

                    } else {
                        ukr = UserKeyRecord.getCurrentValidRecordByPassword(app, username, password, true);
                        if (ukr == null) {
                            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Shouldn't be able to not have a valid key record when OTA restoring with a key server");
                            this.publishProgress(PROGRESS_DONE);
                            return PullTaskResult.UNKNOWN_FAILURE;
                        }
                    }

                    //add to transaction parser factory
                    byte[] wrappedKey = CryptUtil.wrapByteArrayWithString(ukr.getEncryptedKey(), password);
                    factory.initUserParser(wrappedKey);
                } else {
                    factory.initUserParser(CommCareApplication._().getSession().getLoggedInUser().getWrappedKey());

                    //Only purge cases if we already had a logged in user. Otherwise we probably can't read the DB.
                    CaseUtils.purgeCases();
                    useRequestFlags = true;
                }
                //Either way, don't re-do this step
                this.publishProgress(PROGRESS_CLEANED);

                RemoteDataPullResponse pullResponse = dataPullRequester.makeDataPullRequest(this, requestor, server, useRequestFlags);
                Logger.log(AndroidLogger.TYPE_USER, "Request opened. Response code: " + pullResponse.responseCode);

                if (pullResponse.responseCode == 401) {
                    //If we logged in, we need to drop those credentials
                    if (loginNeeded) {
                        CommCareApplication._().releaseUserResourcesAndServices();
                    }
                    Logger.log(AndroidLogger.TYPE_USER, "Bad Auth Request for user!|" + username);
                    return PullTaskResult.AUTH_FAILED;
                } else if (pullResponse.responseCode >= 200 && pullResponse.responseCode < 300) {

                    if (loginNeeded) {
                        //This is necessary (currently) to make sure that data
                        //is encoded. Probably a better way to do this.
                        CommCareApplication._().startUserSession(
                                CryptUtil.unwrapByteArrayWithString(ukr.getEncryptedKey(), password),
                                ukr, restoreSession);
                        wasKeyLoggedIn = true;
                    }


                    this.publishProgress(PROGRESS_AUTHED, 0);
                    Logger.log(AndroidLogger.TYPE_USER, "Remote Auth Successful|" + username);

                    try {
                        BitCache cache = pullResponse.writeResponseToCache(context);

                        InputStream cacheIn = cache.retrieveCache();
                        String syncToken = readInput(cacheIn, factory);
                        updateUserSyncToken(syncToken);

                        //record when we last synced
                        Editor e = prefs.edit();
                        e.putLong("last-succesful-sync", new Date().getTime());
                        e.commit();

                        if (loginNeeded) {
                            CommCareApplication._().getAppStorage(UserKeyRecord.class).write(ukr);
                        }

                        //Let anyone who is listening know!
                        Intent i = new Intent("org.commcare.dalvik.api.action.data.update");
                        this.context.sendBroadcast(i);

                        Logger.log(AndroidLogger.TYPE_USER, "User Sync Successful|" + username);
                        updateCurrentUser(password);
                        this.publishProgress(PROGRESS_DONE);
                        return PullTaskResult.DOWNLOAD_SUCCESS;
                    } catch (InvalidStructureException e) {
                        e.printStackTrace();

                        //TODO: Dump more details!!!
                        Logger.log(AndroidLogger.TYPE_USER, "User Sync failed due to bad payload|" + e.getMessage());
                        return PullTaskResult.BAD_DATA;
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                        Logger.log(AndroidLogger.TYPE_USER, "User Sync failed due to bad payload|" + e.getMessage());
                        return PullTaskResult.BAD_DATA;
                    } catch (UnfullfilledRequirementsException e) {
                        e.printStackTrace();
                        Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "User sync failed oddly, unfulfilled reqs |" + e.getMessage());
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                        Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "User sync failed oddly, ISE |" + e.getMessage());
                    } catch (RecordTooLargeException e) {
                        e.printStackTrace();
                        Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Storage Full during user sync |" + e.getMessage());
                        return PullTaskResult.STORAGE_FULL;
                    }
                } else if (pullResponse.responseCode == 412) {
                    //Our local state is bad. We need to do a full restore.
                    int returnCode = recover(requestor, factory);

                    if (returnCode == PROGRESS_DONE) {
                        //All set! Awesome recovery
                        this.publishProgress(PROGRESS_DONE);
                        return PullTaskResult.DOWNLOAD_SUCCESS;
                    } else if (returnCode == PROGRESS_RECOVERY_FAIL_SAFE) {
                        //Things didn't go super well, but they might next time!

                        //wipe our login if one happened
                        if (loginNeeded) {
                            CommCareApplication._().releaseUserResourcesAndServices();
                        }
                        this.publishProgress(PROGRESS_DONE);
                        return PullTaskResult.UNKNOWN_FAILURE;
                    } else if (returnCode == PROGRESS_RECOVERY_FAIL_BAD) {
                        //WELL! That wasn't so good. TODO: Is there anything 
                        //we can do about this?

                        //wipe our login if one happened
                        if (loginNeeded) {
                            CommCareApplication._().releaseUserResourcesAndServices();
                        }
                        this.publishProgress(PROGRESS_DONE);
                        return PullTaskResult.UNKNOWN_FAILURE;
                    }

                    if (loginNeeded) {
                        CommCareApplication._().releaseUserResourcesAndServices();
                    }
                } else if (pullResponse.responseCode == 500) {
                    if (loginNeeded) {
                        CommCareApplication._().releaseUserResourcesAndServices();
                    }
                    Logger.log(AndroidLogger.TYPE_USER, "500 Server Error|" + username);
                    return PullTaskResult.SERVER_ERROR;
                }


            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Timed out listening to receive data during sync");
                responseError = PullTaskResult.CONNECTION_TIMEOUT;
            } catch (ConnectTimeoutException e) {
                e.printStackTrace();
                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Timed out listening to receive data during sync");
                responseError = PullTaskResult.CONNECTION_TIMEOUT;
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Couldn't sync due network error|" + e.getMessage());
            } catch (UnknownHostException e) {
                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Couldn't sync due to bad network");
                responseError = PullTaskResult.UNREACHABLE_HOST;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Couldn't sync due to IO Error|" + e.getMessage());
            } catch (SessionUnavailableException sue) {
                // TODO PLM: eventually take out this catch. These should be
                // checked locally
                //TODO: Keys were lost somehow.
                sue.printStackTrace();
            }
            if (loginNeeded) {
                CommCareApplication._().releaseUserResourcesAndServices();
            }
            this.publishProgress(PROGRESS_DONE);
            return responseError;
        } finally {
            CommCareSessionService.sessionAliveLock.unlock();
        }
    }

    //TODO: This and the normal sync share a ton of code. It's hard to really... figure out the right way to 
    private int recover(HttpRequestGenerator requestor, AndroidTransactionParserFactory factory) {
        this.publishProgress(PROGRESS_RECOVERY_NEEDED);

        Logger.log(AndroidLogger.TYPE_USER, "Sync Recovery Triggered");


        BitCache cache = null;

        //This chunk is the safe field of operations which can all fail in IO in such a way that we can
        //just report back that things didn't work and don't need to attempt any recovery or additional
        //work
        try {
            // Make a new request without all of the flags
            RemoteDataPullResponse pullResponse = dataPullRequester.makeDataPullRequest(this, requestor, server, false);

            //We basically only care about a positive response, here. Anything else would have been caught by the other request.
            if (!(pullResponse.responseCode >= 200 && pullResponse.responseCode < 300)) {
                return PROGRESS_RECOVERY_FAIL_SAFE;
            }

            //Grab a cache. The plan is to download the incoming data, wipe (move) the existing db, and then
            //restore fresh from the downloaded file
            cache = pullResponse.writeResponseToCache(context);

        } catch (IOException e) {
            e.printStackTrace();
            //Ok, well, we're bailing here, but we didn't make any changes
            Logger.log(AndroidLogger.TYPE_USER, "Sync Recovery Failed due to IOException|" + e.getMessage());
            return PROGRESS_RECOVERY_FAIL_SAFE;
        }


        this.publishProgress(PROGRESS_RECOVERY_STARTED);
        Logger.log(AndroidLogger.TYPE_USER, "Sync Recovery payload downloaded");

        //Ok. Here's where things get real. We now have a stable copy of the fresh data from the
        //server, so it's "safe" for us to wipe the casedb copy of it.

        //CTS: We're not doing this in a super good way right now, need to be way more fault tolerant.
        //this is the temporary implementation of everything past this point

        //Wipe storage
        //TODO: move table instead. Should be straightforward with sandboxed db's
        CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class).removeAll();


        String failureReason = "";
        try {
            //Get new data
            String syncToken = readInput(cache.retrieveCache(), factory);
            updateUserSyncToken(syncToken);
            Logger.log(AndroidLogger.TYPE_USER, "Sync Recovery Succesful");
            return PROGRESS_DONE;
        } catch (InvalidStructureException e) {
            e.printStackTrace();
            failureReason = e.getMessage();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            failureReason = e.getMessage();
        } catch (UnfullfilledRequirementsException e) {
            e.printStackTrace();
            failureReason = e.getMessage();
        } catch (StorageFullException e) {
            e.printStackTrace();
            failureReason = e.getMessage();
        }

        //These last two aren't a sign that the incoming data is bad, but
        //we still can't recover from them usefully
        catch (SessionUnavailableException e) {
            e.printStackTrace();
            failureReason = e.getMessage();
        } catch (IOException e) {
            e.printStackTrace();
            failureReason = e.getMessage();
        } finally {
            //destroy temp file
            cache.release();
        }

        //OK, so we would have returned success by now if things had worked out, which means that instead we got an error
        //while trying to parse everything out. We need to recover from that error here and rollback the changes

        //TODO: Roll back changes
        Logger.log(AndroidLogger.TYPE_USER, "Sync recovery failed|" + failureReason);
        return PROGRESS_RECOVERY_FAIL_BAD;
    }

    private void updateCurrentUser(String password) throws SessionUnavailableException {
        SqlStorage<User> storage = CommCareApplication._().getUserStorage("USER", User.class);
        User u = storage.getRecordForValue(User.META_USERNAME, username);
        CommCareApplication._().getSession().setCurrentUser(u, password);
    }

    private void updateUserSyncToken(String syncToken) throws StorageFullException {
        SqlStorage<User> storage = CommCareApplication._().getUserStorage("USER", User.class);
        try {
            User u = storage.getRecordForValue(User.META_USERNAME, username);
            u.setLastSyncToken(syncToken);
            storage.write(u);
        } catch (NoSuchElementException nsee) {
            //TODO: Something here? Maybe figure out if we downloaded a user from the server and attach the data to it?
        }
    }

    private String readInput(InputStream stream, AndroidTransactionParserFactory factory) throws InvalidStructureException, IOException,
            XmlPullParserException, UnfullfilledRequirementsException, SessionUnavailableException {
        DataModelPullParser parser;

        factory.initCaseParser();
        factory.initStockParser();

        Hashtable<String, String> formNamespaces = FormSaveUtil.getNamespaceToFilePathMap(context);
        factory.initFormInstanceParser(formNamespaces);

        //this is _really_ coupled, but we'll tolerate it for now because of the absurd performance gains
        SQLiteDatabase db = CommCareApplication._().getUserDbHandle();
        try {
            db.beginTransaction();
            parser = new DataModelPullParser(stream, factory, true, false, this);
            parser.parse();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        //Return the sync token ID
        return factory.getSyncToken();
    }

    //BEGIN - OTA Listener methods below - Note that most of the methods
    //below weren't really implemented

    @Override
    public void onUpdate(int numberCompleted) {
        mCurrentProgress = numberCompleted;
        int miliSecElapsed = (int)(System.currentTimeMillis() - mSyncStartTime);

        this.publishProgress(PROGRESS_PROCESSING, mCurrentProgress, mTotalItems, miliSecElapsed);
    }

    @Override
    public void setTotalForms(int totalItemCount) {
        mTotalItems = totalItemCount;
        mCurrentProgress = 0;
        mSyncStartTime = System.currentTimeMillis();
        this.publishProgress(PROGRESS_PROCESSING, mCurrentProgress, mTotalItems, 0);
    }

    @Override
    public void statusUpdate(int statusNumber) {
    }

    @Override
    public void refreshView() {
    }

    @Override
    public void getCredentials() {
    }

    @Override
    public void promptRetry(String msg) {
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public void onFailure(String failMessage) {
    }

    public void reportDownloadProgress(int totalRead) {
        publishProgress(DataPullTask.PROGRESS_DOWNLOADING, totalRead);
    }

    public enum PullTaskResult {
        DOWNLOAD_SUCCESS(-1),
        AUTH_FAILED(GoogleAnalyticsFields.VALUE_AUTH_FAILED),
        BAD_DATA(GoogleAnalyticsFields.VALUE_BAD_DATA),
        UNKNOWN_FAILURE(GoogleAnalyticsFields.VALUE_UNKNOWN_FAILURE),
        UNREACHABLE_HOST(GoogleAnalyticsFields.VALUE_UNREACHABLE_HOST),
        CONNECTION_TIMEOUT(GoogleAnalyticsFields.VALUE_CONNECTION_TIMEOUT),
        SERVER_ERROR(GoogleAnalyticsFields.VALUE_SERVER_ERROR),
        STORAGE_FULL(GoogleAnalyticsFields.VALUE_STORAGE_FULL);

        private final int googleAnalyticsValue;

        PullTaskResult(int googleAnalyticsValue) {
            this.googleAnalyticsValue = googleAnalyticsValue;
        }

        public int getCorrespondingGoogleAnalyticsValue() {
            return googleAnalyticsValue;
        }

        public String getCorrespondingGoogleAnalyticsLabel() {
            if (this == DOWNLOAD_SUCCESS) {
                return GoogleAnalyticsFields.LABEL_SYNC_SUCCESS;
            } else {
                return GoogleAnalyticsFields.LABEL_SYNC_FAILURE;
            }
        }
    }
}
