package org.commcare.tasks;

import android.content.Context;
import android.content.Intent;
import android.support.v4.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.conn.ConnectTimeoutException;
import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.ACase;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.engine.cases.CaseUtils;
import org.commcare.interfaces.HttpRequestEndpoints;
import org.commcare.logging.AndroidLogger;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.models.encryption.CryptUtil;
import org.commcare.modern.models.RecordTooLargeException;
import org.commcare.network.DataPullRequester;
import org.commcare.network.RemoteDataPullResponse;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.resources.model.CommCareOTARestoreListener;
import org.commcare.services.CommCareSessionService;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.FormSaveUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.UnknownSyncError;
import org.commcare.utils.bitcache.BitCache;
import org.commcare.xml.AndroidTransactionParserFactory;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.xml.ElementParser;
import org.javarosa.xml.util.ActionableInvalidStructureException;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
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
public abstract class DataPullTask<R> 
    extends CommCareTask<Void, Integer, ResultAndError<DataPullTask.PullTaskResult>, R>
        implements CommCareOTARestoreListener {
    private final String server;
    private final String username;
    private final String password;
    private final Context context;

    private int mCurrentProgress;
    private int mTotalItems;
    private long mSyncStartTime;

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
    public static final int PROGRESS_DOWNLOADING_COMPLETE = 512;
    public static final int PROGRESS_SERVER_PROCESSING = 1024;

    private DataPullRequester dataPullRequester;

    private boolean loginNeeded;
    private UserKeyRecord ukrForLogin;
    private boolean wasKeyLoggedIn;

    private long waitPeriodBeginTime = -1;
    public long retryAtTime = -1;
    public int serverProgressCompleted = -1;
    private int serverProgressTotal = -1;
    private int retryLimit = -1;
    public int numTries = 0;

    public DataPullTask(String username, String password,
                         String server, Context context, DataPullRequester dataPullRequester) {
        this.server = server;
        this.username = username;
        this.password = password;
        this.context = context;
        this.taskId = DATA_PULL_TASK_ID;
        this.dataPullRequester = dataPullRequester;
        this.requestor = dataPullRequester.getHttpGenerator(username, password);

        TAG = DataPullTask.class.getSimpleName();
    }

    public DataPullTask(String username, String password,
                        String server, Context context) {
        this(username, password, server, context, CommCareApplication._().getDataPullRequester());
    }

    // TODO PLM: once this task is refactored into manageable components, it should use the
    // ManagedAsyncTask pattern of checking for isCancelled() and aborting at safe places.
    @Override
    protected void onCancelled() {
        super.onCancelled();
        wipeLoginIfItOccurred();
    }
    private HttpRequestEndpoints requestor;

    @Override
    protected ResultAndError<PullTaskResult> doTaskBackground(Void... params) {
        if (!CommCareSessionService.sessionAliveLock.tryLock()) {
            // Don't try to sync if logging out is occurring
            return new ResultAndError<>(PullTaskResult.UNKNOWN_FAILURE,
                    "Cannot sync while a logout is in process");
        }
        try {
            return doTaskBackgroundHelper();
        } finally {
            CommCareSessionService.sessionAliveLock.unlock();
        }
    }

    private ResultAndError<PullTaskResult> doTaskBackgroundHelper() {
        publishProgress(PROGRESS_STARTED);
        recordSyncAttemptTime();
        Logger.log(AndroidLogger.TYPE_USER, "Starting Sync");
        determineIfLoginNeeded();

        AndroidTransactionParserFactory factory = getTransactionParserFactory();
        byte[] wrappedEncryptionKey = getEncryptionKey();
        if (wrappedEncryptionKey == null) {
            this.publishProgress(PROGRESS_DONE);
            return new ResultAndError<>(PullTaskResult.UNKNOWN_FAILURE,
                    "Unable to get or generate encryption key");
        }

        factory.initUserParser(wrappedEncryptionKey);
        if (!loginNeeded) {
            //Only purge cases if we already had a logged in user. Otherwise we probably can't read the DB.
            CaseUtils.purgeCases();
        }

        return getRequestResultOrRetry(factory);
    }

    private void determineIfLoginNeeded() {
        try {
            loginNeeded = !CommCareApplication._().getSession().isActive();
        } catch (SessionUnavailableException sue) {
            // expected if we aren't initialized.
            loginNeeded = true;
        }
    }

    private AndroidTransactionParserFactory getTransactionParserFactory() {
        return new AndroidTransactionParserFactory(context, requestor) {
            boolean publishedAuth = false;

            @Override
            public void reportProgress(int progress) {
                if (!publishedAuth) {
                    DataPullTask.this.publishProgress(PROGRESS_AUTHED, progress);
                    publishedAuth = true;
                }
            }
        };
    }

    private byte[] getEncryptionKey() {
        byte[] key;
        if (loginNeeded) {
            initUKRForLogin();
            if (ukrForLogin == null) {
                return null;
            }
            key = ukrForLogin.getEncryptedKey();
        } else {
            key = CommCareApplication._().getSession().getLoggedInUser().getWrappedKey();
        }
        this.publishProgress(PROGRESS_CLEANED); // Either way, we don't want to do this step again
        return key;
    }

    private void initUKRForLogin() {
        if (shouldGenerateFirstKey()) {
            SecretKey newKey = CryptUtil.generateSemiRandomKey();
            if (newKey == null) {
                return;
            }
            String sandboxId = PropertyUtils.genUUID().replace("-", "");
            ukrForLogin = new UserKeyRecord(username, UserKeyRecord.generatePwdHash(password),
                    ByteEncrypter.wrapByteArrayWithString(newKey.getEncoded(), password),
                    new Date(), new Date(Long.MAX_VALUE), sandboxId);
        } else {
            ukrForLogin = UserKeyRecord.getCurrentValidRecordByPassword(
                    CommCareApplication._().getCurrentApp(), username, password, true);
            if (ukrForLogin == null) {
                Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                        "Shouldn't be able to not have a valid key record when OTA restoring with a key server");
            }
        }
    }

    private static boolean shouldGenerateFirstKey() {
        String keyServer = CommCarePreferences.getKeyServer();
        return keyServer == null || keyServer.equals("");
    }

    private ResultAndError<PullTaskResult> getRequestResultOrRetry(AndroidTransactionParserFactory factory) {
        if (retryLimit != -1 && numTries >= retryLimit) {
            return new ResultAndError<>(PullTaskResult.RETRY_LIMIT_EXCEEDED);
        }

        waitAndUpdateDuringRetryPeriod();

        if (isCancelled()) {
            return new ResultAndError<>(PullTaskResult.UNKNOWN_FAILURE, "");
        }

        PullTaskResult responseError = PullTaskResult.UNKNOWN_FAILURE;
        retryAtTime = -1;
        try {
            ResultAndError<PullTaskResult> result = makeRequestAndHandleResponse(factory);
            if (PullTaskResult.RETRY_NEEDED.equals(result.data)) {
                return getRequestResultOrRetry(factory);
            } else {
                return result;
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
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Couldn't sync due to bad network");
            responseError = PullTaskResult.UNREACHABLE_HOST;
        } catch (IOException e) {
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Couldn't sync due to IO Error|" + e.getMessage());
        } catch (UnknownSyncError e) {
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Couldn't sync due to Unknown Error|" + e.getMessage());
        }

        wipeLoginIfItOccurred();
        this.publishProgress(PROGRESS_DONE);
        return new ResultAndError<>(responseError);
    }

    private void waitAndUpdateDuringRetryPeriod() {
        while (retryAtTime != -1 && retryAtTime > System.currentTimeMillis()) {
            if (isCancelled()) {
                // stop waiting if the user canceled the task
                return;
            }
            reportServerProgress();
        }
    }

    /*
     * @return the proper result, or null if we have not yet been able to determine the result to
     * return
     * @throws IOException
     */
    private ResultAndError<PullTaskResult> makeRequestAndHandleResponse(AndroidTransactionParserFactory factory)
            throws IOException, UnknownSyncError {

        numTries++;
        RemoteDataPullResponse pullResponse =
                dataPullRequester.makeDataPullRequest(this, requestor, server, !loginNeeded);
        int responseCode = pullResponse.responseCode;
        Logger.log(AndroidLogger.TYPE_USER,
                "Request opened. Response code: " + responseCode);

        if (responseCode == 401) {
            return handleAuthFailed();
        } else if (responseCode >= 200 && responseCode < 300) {
            if (responseCode == 202) {
                return handleRetryResponseCode(pullResponse);
            } else {
                return handleSuccessResponseCode(pullResponse, factory);
            }
        } else if (responseCode == 412) {
            return handleBadLocalState(factory);
        } else if (responseCode == 500) {
            return handleServerError();
        } else {
            throw new UnknownSyncError();
        }
    }

    private ResultAndError<PullTaskResult> handleAuthFailed() {
        wipeLoginIfItOccurred();
        Logger.log(AndroidLogger.TYPE_USER, "Bad Auth Request for user!|" + username);
        return new ResultAndError<>(PullTaskResult.AUTH_FAILED);
    }

    private ResultAndError<PullTaskResult> handleRetryResponseCode(RemoteDataPullResponse response) {
        String headerValue = response.retryHeader.getValue();
        try {
            long waitTimeInMilliseconds = Integer.parseInt(headerValue) * 1000;
            waitPeriodBeginTime = System.currentTimeMillis();
            retryAtTime = waitPeriodBeginTime + waitTimeInMilliseconds;
            parseProgressFromRetryResult(response);
            return new ResultAndError<>(PullTaskResult.RETRY_NEEDED);
        } catch (NumberFormatException e) {
            Logger.log(AndroidLogger.TYPE_USER, "Invalid Retry-After header value: " + headerValue);
            return new ResultAndError<>(PullTaskResult.BAD_DATA);
        }
    }

    /**
     * @return the proper result, or null if we have not yet been able to determine the result to
     * return
     * @throws IOException
     */
    private ResultAndError<PullTaskResult> handleSuccessResponseCode(
            RemoteDataPullResponse pullResponse, AndroidTransactionParserFactory factory)
            throws IOException, UnknownSyncError {

        handleLoginNeededOnSuccess();
        this.publishProgress(PROGRESS_AUTHED, 0);

        if (isCancelled()) {
            // About to enter data commit phase; last chance to finish early if cancelled.
            return new ResultAndError<>(PullTaskResult.UNKNOWN_FAILURE, "");
        }

        this.publishProgress(PROGRESS_DOWNLOADING_COMPLETE, 0);
        Logger.log(AndroidLogger.TYPE_USER, "Remote Auth Successful|" + username);

        try {
            BitCache cache = pullResponse.writeResponseToCache(context);
            String syncToken = readInput(cache.retrieveCache(), factory);
            updateUserSyncToken(syncToken);

            onSuccessfulSync();
            return new ResultAndError<>(PullTaskResult.DOWNLOAD_SUCCESS);
        } catch (XmlPullParserException e) {
            wipeLoginIfItOccurred();
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_USER,
                    "User Sync failed due to bad payload|" + e.getMessage());
            return new ResultAndError<>(PullTaskResult.BAD_DATA, e.getMessage());
        } catch (ActionableInvalidStructureException e) {
            wipeLoginIfItOccurred();
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_USER,
                    "User Sync failed due to bad payload|" + e.getMessage());
            return new ResultAndError<>(PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION,
                    e.getLocalizedMessage());
        } catch (InvalidStructureException e) {
            wipeLoginIfItOccurred();
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_USER,
                    "User Sync failed due to bad payload|" + e.getMessage());
            return new ResultAndError<>(PullTaskResult.BAD_DATA, e.getMessage());
        } catch (UnfullfilledRequirementsException e) {
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                    "User sync failed oddly, unfulfilled reqs |" + e.getMessage());
            throw new UnknownSyncError();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                    "User sync failed oddly, ISE |" + e.getMessage());
            throw new UnknownSyncError();
        } catch (RecordTooLargeException e) {
            wipeLoginIfItOccurred();
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                    "Storage Full during user sync |" + e.getMessage());
            return new ResultAndError<>(PullTaskResult.STORAGE_FULL);
        }
    }

    private void handleLoginNeededOnSuccess() {
        if (loginNeeded) {
            // This is currently necessary to make sure that data is encoded, but there is
            // probably a better way to do it
            CommCareApplication._().startUserSession(
                    ByteEncrypter.unwrapByteArrayWithString(ukrForLogin.getEncryptedKey(), password),
                    ukrForLogin, false);
            wasKeyLoggedIn = true;
        }
    }

    /**
     * @return the proper result, or null if we have not yet been able to determine the result to
     * return
     */
    private ResultAndError<PullTaskResult> handleBadLocalState(AndroidTransactionParserFactory factory)
            throws UnknownSyncError {
        Pair<Integer, String> returnCodeAndMessageFromRecovery = recover(requestor, factory);
        int returnCode = returnCodeAndMessageFromRecovery.first;
        String failureReason = returnCodeAndMessageFromRecovery.second;

        if (returnCode == PROGRESS_DONE) {
            // Recovery was successful
            onSuccessfulSync();
            return new ResultAndError<>(PullTaskResult.DOWNLOAD_SUCCESS);
        } else if (returnCode == PROGRESS_RECOVERY_FAIL_SAFE || returnCode == PROGRESS_RECOVERY_FAIL_BAD) {
            wipeLoginIfItOccurred();
            this.publishProgress(PROGRESS_DONE);
            return new ResultAndError<>(PullTaskResult.UNKNOWN_FAILURE, failureReason);
        } else {
            throw new UnknownSyncError();
        }
    }

    private void onSuccessfulSync() {
        recordSuccessfulSyncTime();

        Intent i = new Intent("org.commcare.dalvik.api.action.data.update");
        this.context.sendBroadcast(i);

        if (loginNeeded) {
            CommCareApplication._().getAppStorage(UserKeyRecord.class).write(ukrForLogin);
        }

        Logger.log(AndroidLogger.TYPE_USER, "User Sync Successful|" + username);
        updateCurrentUser(password);
        this.publishProgress(PROGRESS_DONE);
    }

    private ResultAndError<PullTaskResult> handleServerError() {
        wipeLoginIfItOccurred();
        Logger.log(AndroidLogger.TYPE_USER, "500 Server Error|" + username);
        return new ResultAndError<>(PullTaskResult.SERVER_ERROR, "");
    }

    private void wipeLoginIfItOccurred() {
        if (wasKeyLoggedIn) {
            CommCareApplication._().releaseUserResourcesAndServices();
        }
    }

    @Override
    public void tryAbort() {
        if (requestor != null) {
            requestor.abortCurrentRequest();
        }
    }

    private static void recordSyncAttemptTime() {
        //TODO: This should be per _user_, not per app
        CommCareApplication._().getCurrentApp().getAppPreferences().edit()
                .putLong("last-ota-restore", new Date().getTime()).commit();
    }

    private static void recordSuccessfulSyncTime() {
        CommCareApplication._().getCurrentApp().getAppPreferences().edit()
                .putLong("last-succesful-sync", new Date().getTime()).commit();
    }

    //TODO: This and the normal sync share a ton of code. It's hard to really... figure out the right way to 
    private Pair<Integer, String> recover(HttpRequestEndpoints requestor, AndroidTransactionParserFactory factory) {
        this.publishProgress(PROGRESS_RECOVERY_NEEDED);

        Logger.log(AndroidLogger.TYPE_USER, "Sync Recovery Triggered");

        BitCache cache;

        //This chunk is the safe field of operations which can all fail in IO in such a way that we can
        //just report back that things didn't work and don't need to attempt any recovery or additional
        //work
        try {
            // Make a new request without all of the flags
            RemoteDataPullResponse pullResponse = dataPullRequester.makeDataPullRequest(this, requestor, server, false);

            //We basically only care about a positive response, here. Anything else would have been caught by the other request.
            if (!(pullResponse.responseCode >= 200 && pullResponse.responseCode < 300)) {
                return new Pair<>(PROGRESS_RECOVERY_FAIL_SAFE, "");
            }

            //Grab a cache. The plan is to download the incoming data, wipe (move) the existing db, and then
            //restore fresh from the downloaded file
            cache = pullResponse.writeResponseToCache(context);
        } catch (IOException e) {
            e.printStackTrace();
            //Ok, well, we're bailing here, but we didn't make any changes
            Logger.log(AndroidLogger.TYPE_USER, "Sync Recovery Failed due to IOException|" + e.getMessage());
            return new Pair<>(PROGRESS_RECOVERY_FAIL_SAFE, "");
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
            return new Pair<>(PROGRESS_DONE, "");
        } catch (ActionableInvalidStructureException e) {
            e.printStackTrace();
            failureReason = e.getLocalizedMessage();
        } catch (InvalidStructureException | XmlPullParserException
                | UnfullfilledRequirementsException | StorageFullException
                | SessionUnavailableException | IOException e) {
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
        return new Pair<>(PROGRESS_RECOVERY_FAIL_BAD, failureReason);
    }

    private void updateCurrentUser(String password) {
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

    private void parseProgressFromRetryResult(RemoteDataPullResponse response) {
        try {
            BitCache cache = response.writeResponseToCache(context);
            InputStream stream = cache.retrieveCache();
            KXmlParser parser = ElementParser.instantiateParser(stream);
            parser.next();
            int eventType = parser.getEventType();
            do {
                if (eventType == KXmlParser.START_TAG) {
                    if (parser.getName().toLowerCase().equals("progress")) {
                        serverProgressCompleted = Integer.parseInt(parser.getAttributeValue(null, "done"));
                        serverProgressTotal = Integer.parseInt(parser.getAttributeValue(null, "total"));
                        return;
                    }
                }
                eventType = parser.next();
            } while (eventType != KXmlParser.END_DOCUMENT);
        } catch (IOException | XmlPullParserException e) {
            Logger.log(AndroidLogger.TYPE_USER, "Error while parsing progress values of retry result");
        }
    }

    // currently for testing purposes only
    public void setRetryLimitForAsyncRestore(int limit) {
        this.retryLimit = limit;
    }

    private String readInput(InputStream stream, AndroidTransactionParserFactory factory)
            throws InvalidStructureException, IOException, XmlPullParserException,
            UnfullfilledRequirementsException {
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
        int millisecondsElapsed = (int)(System.currentTimeMillis() - mSyncStartTime);

        this.publishProgress(PROGRESS_PROCESSING, mCurrentProgress, mTotalItems, millisecondsElapsed);
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

    //TODO: flesh this out more
    private void reportServerProgress() {
        reportServerProgressByTime();
    }

    private void reportServerProgressByTime() {
        int secondsUntilNextAttempt = (int)((retryAtTime - System.currentTimeMillis()) / 1000);
        int secondsSinceWaitPeriodStart = (int)((System.currentTimeMillis() - waitPeriodBeginTime) / 1000);
        this.publishProgress(PROGRESS_SERVER_PROCESSING, secondsUntilNextAttempt, secondsSinceWaitPeriodStart);
    }

    private void reportServerProgressByPortionCompleted() {
        //TODO: implement
    }

    public enum PullTaskResult {
        DOWNLOAD_SUCCESS(-1),
        RETRY_NEEDED(-1),
        RETRY_LIMIT_EXCEEDED(-1),
        AUTH_FAILED(GoogleAnalyticsFields.VALUE_AUTH_FAILED),
        BAD_DATA(GoogleAnalyticsFields.VALUE_BAD_DATA),
        BAD_DATA_REQUIRES_INTERVENTION(GoogleAnalyticsFields.VALUE_BAD_DATA_REQUIRES_INTERVENTION),
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
