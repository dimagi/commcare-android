package org.commcare.tasks;

import android.content.Context;
import androidx.core.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.util.InvalidCaseGraphException;
import org.commcare.core.encryption.CryptUtil;
import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.core.network.CaptivePortalRedirectException;
import org.commcare.core.network.bitcache.BitCache;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.engine.cases.CaseUtils;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.interfaces.CommcareRequestEndpoints;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.models.database.user.models.EntityStorageCache;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.modern.models.RecordTooLargeException;
import org.commcare.network.DataPullRequester;
import org.commcare.network.HttpUtils;
import org.commcare.network.RemoteDataPullResponse;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.resources.model.CommCareOTARestoreListener;
import org.commcare.services.CommCareSessionService;
import org.commcare.sync.ExternalDataUpdateHelper;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.util.EncryptionKeyHelper;
import org.commcare.util.LogTypes;
import org.commcare.utils.FormSaveUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.SyncDetailCalculations;
import org.commcare.utils.UnknownSyncError;
import org.commcare.xml.AndroidTransactionParserFactory;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.xml.util.ActionableInvalidStructureException;
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
import javax.net.ssl.SSLException;

/**
 * @author ctsims
 */
public abstract class DataPullTask<R>
        extends CommCareTask<Void, Integer, ResultAndError<DataPullTask.PullTaskResult>, R>
        implements CommCareOTARestoreListener {
    private final String server;
    private final String username;
    private final String password;
    protected final Context context;

    private int mCurrentProgress;
    private int mTotalItems;
    private long mSyncStartTime;

    public static final int DATA_PULL_TASK_ID = 10;

    public static final int PROGRESS_STARTED = 0;
    public static final int PROGRESS_CLEANED = 1;
    public static final int PROGRESS_AUTHED = 2;
    public static final int PROGRESS_DONE = 4;
    public static final int PROGRESS_RECOVERY_NEEDED = 8;
    public static final int PROGRESS_RECOVERY_STARTED = 16;
    private static final int PROGRESS_RECOVERY_FAIL_SAFE = 32;
    private static final int PROGRESS_RECOVERY_FAIL_BAD = 64;
    public static final int PROGRESS_PROCESSING = 128;
    public static final int PROGRESS_DOWNLOADING = 256;
    public static final int PROGRESS_DOWNLOADING_COMPLETE = 512;
    public static final int PROGRESS_SERVER_PROCESSING = 1024;

    private final DataPullRequester dataPullRequester;
    private final AsyncRestoreHelper asyncRestoreHelper;
    private final boolean blockRemoteKeyManagement;

    private boolean loginNeeded;
    private UserKeyRecord ukrForLogin;
    private boolean wasKeyLoggedIn;
    private boolean skipFixtures;

    public DataPullTask(String username, String password, String userId,
                        String server, Context context, DataPullRequester dataPullRequester,
                        boolean blockRemoteKeyManagement, boolean skipFixtures) {
        this.skipFixtures = skipFixtures;
        this.server = server;
        this.username = username;
        this.password = password;
        this.context = context;
        this.taskId = DATA_PULL_TASK_ID;
        this.dataPullRequester = dataPullRequester;
        this.requestor = dataPullRequester.getHttpGenerator(username, password, userId);
        this.asyncRestoreHelper = CommCareApplication.instance().getAsyncRestoreHelper(this);
        this.blockRemoteKeyManagement = blockRemoteKeyManagement;
        TAG = DataPullTask.class.getSimpleName();
    }

    public DataPullTask(String username, String password, String userId,
                        String server, Context context, boolean skipFixtures) {
        this(username, password, userId, server, context, CommCareApplication.instance().getDataPullRequester(),
                false, skipFixtures);
    }

    // TODO PLM: once this task is refactored into manageable components, it should use the
    // ManagedAsyncTask pattern of checking for isCancelled() and aborting at safe places.
    @Override
    protected void onCancelled() {
        super.onCancelled();
        wipeLoginIfItOccurred();
    }

    private final CommcareRequestEndpoints requestor;

    @Override
    protected ResultAndError<PullTaskResult> doTaskBackground(Void... params) {
        if (!CommCareSessionService.sessionAliveLock.tryLock()) {
            // Don't try to sync if logging out is occurring
            return new ResultAndError<>(PullTaskResult.SESSION_EXPIRE,
                    "Cannot sync while a logout is in process");
        }
        try {
            return doTaskBackgroundHelper();
        } finally {
            CommCareSessionService.sessionAliveLock.unlock();
        }
    }

    private ResultAndError<PullTaskResult> doTaskBackgroundHelper() {
        if (StringUtils.isEmpty(server)) {
            return new ResultAndError<>(PullTaskResult.EMPTY_URL, Localization.get("sync.fail.empty.url"));
        }

        publishProgress(PROGRESS_STARTED);
        HiddenPreferences.setPostUpdateSyncNeeded(false);
        Logger.log(LogTypes.TYPE_USER, "Starting Sync");
        determineIfLoginNeeded();

        AndroidTransactionParserFactory factory = getTransactionParserFactory();
        byte[] wrappedEncryptionKey = getEncryptionKey();
        if (wrappedEncryptionKey == null) {
            this.publishProgress(PROGRESS_DONE);
            return new ResultAndError<>(PullTaskResult.ENCRYPTION_FAILURE,
                    "Unable to get or generate encryption key");
        }

        factory.initUserParser(wrappedEncryptionKey);

        if (!loginNeeded) {
            //Only purge cases if we already had a logged in user. Otherwise we probably can't read the DB.
            try {
                CaseUtils.purgeCases();
            } catch (InvalidCaseGraphException e) {
                try {
                    return handleBadLocalState(factory);
                } catch (UnknownSyncError unknownSyncError) {
                    e.printStackTrace();
                    Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Couldn't sync due to Unknown Error|" + e.getMessage());
                    return new ResultAndError<>(PullTaskResult.UNKNOWN_FAILURE);
                }
            }
        }

        return getRequestResultOrRetry(factory);
    }

    private void determineIfLoginNeeded() {
        try {
            loginNeeded = !CommCareApplication.instance().getSession().isActive();
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
            key = CommCareApplication.instance().getSession().getLoggedInUser().getWrappedKey();
        }
        this.publishProgress(PROGRESS_CLEANED); // Either way, we don't want to do this step again
        return key;
    }

    private void initUKRForLogin() {
        if (blockRemoteKeyManagement || shouldGenerateFirstKey()) {
            SecretKey newKey = null;
            try {
                newKey = CryptUtil.generateRandomSecretKey();
            } catch (EncryptionKeyHelper.EncryptionKeyException e) {
                return;
            }
            String sandboxId = PropertyUtils.genUUID().replace("-", "");
            ukrForLogin = new UserKeyRecord(username, UserKeyRecord.generatePwdHash(password),
                    ByteEncrypter.wrapByteArrayWithString(newKey.getEncoded(), password),
                    new Date(), new Date(Long.MAX_VALUE), sandboxId);
        } else {
            ukrForLogin = UserKeyRecord.getCurrentValidRecordByPassword(
                    CommCareApplication.instance().getCurrentApp(), username, password, true);
            if (ukrForLogin == null) {
                Logger.log(LogTypes.TYPE_ERROR_ASSERTION,
                        "Shouldn't be able to not have a valid key record when OTA restoring with a key server");
            }
        }
    }

    private static boolean shouldGenerateFirstKey() {
        String keyServer = ServerUrls.getKeyServer();
        return keyServer == null || keyServer.equals("");
    }

    private ResultAndError<PullTaskResult> getRequestResultOrRetry(AndroidTransactionParserFactory factory) {
        while (asyncRestoreHelper.retryWaitPeriodInProgress()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }

            if (isCancelled()) {
                return new ResultAndError<>(PullTaskResult.CANCELLED);
            }
        }

        PullTaskResult responseError = PullTaskResult.UNKNOWN_FAILURE;
        asyncRestoreHelper.retryAtTime = -1;
        try {
            ResultAndError<PullTaskResult> result = makeRequestAndHandleResponse(factory);
            if (PullTaskResult.RETRY_NEEDED.equals(result.data)) {
                asyncRestoreHelper.startReportingServerProgress();
                return getRequestResultOrRetry(factory);
            } else {
                return result;
            }
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Timed out listening to receive data during sync");
            responseError = PullTaskResult.CONNECTION_TIMEOUT;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Couldn't sync due to bad network");
            responseError = PullTaskResult.UNREACHABLE_HOST;
        } catch (AuthenticationInterceptor.PlainTextPasswordException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE, "Encountered PlainTextPasswordException during sync: Sending password over HTTP");
            responseError = PullTaskResult.AUTH_OVER_HTTP;
        } catch (CaptivePortalRedirectException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Couldn't sync due to presense of captive portal");
            responseError = PullTaskResult.CAPTIVE_PORTAL;
        } catch(SSLException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Couldn't sync due to SSL error");
            responseError = PullTaskResult.BAD_CERTIFICATE;
        } catch (IOException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Couldn't sync due to IO Error|" + e.getMessage());
        } catch (UnknownSyncError e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Couldn't sync due to Unknown Error|" + e.getMessage());
        } catch (EncryptionKeyHelper.EncryptionKeyException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Couldn't sync due to Cache encryption Error|" + e.getMessage());
        }

        wipeLoginIfItOccurred();
        this.publishProgress(PROGRESS_DONE);
        return new ResultAndError<>(responseError);
    }

    /**
     * @return the proper result, or null if we have not yet been able to determine the result to
     * return
     */
    private ResultAndError<PullTaskResult> makeRequestAndHandleResponse(
            AndroidTransactionParserFactory factory)
            throws IOException, UnknownSyncError, EncryptionKeyHelper.EncryptionKeyException {

        RemoteDataPullResponse pullResponse =
                dataPullRequester.makeDataPullRequest(this, requestor, server, !loginNeeded, skipFixtures);

        int responseCode = pullResponse.responseCode;
        Logger.log(LogTypes.TYPE_USER,
                "Data pull request opened. Response code: " + responseCode);

        if (responseCode == 401) {
            return handleAuthFailed();
        } else if (responseCode >= 200 && responseCode < 300) {
            if (responseCode == 202) {
                return asyncRestoreHelper.handleRetryResponseCode(pullResponse);
            } else {
                return handleSuccessResponseCode(pullResponse, factory);
            }
        } else if (responseCode == 412) {
            return handleBadLocalState(factory);
        } else if (responseCode == 406) {
            return processErrorResponseWithMessage(pullResponse);
        } else if (responseCode == 500) {
            return handleServerError();
        } else if (responseCode == 503 || responseCode == 429) {
            return handleRateLimitedError();
        } else {
            throw new UnknownSyncError();
        }
    }

    private ResultAndError<PullTaskResult> processErrorResponseWithMessage(RemoteDataPullResponse pullResponse) {
        return new ResultAndError<>(PullTaskResult.ACTIONABLE_FAILURE, HttpUtils.parseUserVisibleError(pullResponse.getResponse()));
    }

    private ResultAndError<PullTaskResult> handleAuthFailed() {
        wipeLoginIfItOccurred();
        Logger.log(LogTypes.TYPE_USER, "Bad Auth Request for user!|" + username);
        return new ResultAndError<>(PullTaskResult.AUTH_FAILED);
    }

    /**
     * @return the proper result, or null if we have not yet been able to determine the result to
     * return
     */
    private ResultAndError<PullTaskResult> handleSuccessResponseCode(
            RemoteDataPullResponse pullResponse, AndroidTransactionParserFactory factory)
            throws IOException, UnknownSyncError, EncryptionKeyHelper.EncryptionKeyException {

        asyncRestoreHelper.completeServerProgressBarIfShowing();
        handleLoginNeededOnSuccess();
        this.publishProgress(PROGRESS_AUTHED, 0);

        if (isCancelled()) {
            // About to enter data commit phase; last chance to finish early if cancelled.
            return new ResultAndError<>(PullTaskResult.CANCELLED);
        }

        this.publishProgress(PROGRESS_DOWNLOADING_COMPLETE, 0);
        Logger.log(LogTypes.TYPE_USER, "Remote Auth Successful|" + username);

        try {
            BitCache cache = pullResponse.writeResponseToCache(context);
            String syncToken = readInput(cache.retrieveCache(), factory);
            updateUserSyncToken(syncToken);

            onSuccessfulSync();
            return new ResultAndError<>(PullTaskResult.DOWNLOAD_SUCCESS);
        } catch (XmlPullParserException e) {
            wipeLoginIfItOccurred();
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_USER,
                    "User Sync failed due to bad payload|" + e.getMessage());
            return new ResultAndError<>(PullTaskResult.BAD_DATA, e.getMessage());
        } catch (ActionableInvalidStructureException e) {
            wipeLoginIfItOccurred();
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_USER,
                    "User Sync failed due to bad payload|" + e.getMessage());
            return new ResultAndError<>(PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION,
                    e.getLocalizedMessage());
        } catch (InvalidStructureException e) {
            wipeLoginIfItOccurred();
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_USER,
                    "User Sync failed due to bad payload|" + e.getMessage());
            return new ResultAndError<>(PullTaskResult.BAD_DATA, e.getMessage());
        } catch (UnfullfilledRequirementsException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_ERROR_ASSERTION,
                    "User sync failed oddly, unfulfilled reqs |" + e.getMessage());
            throw new UnknownSyncError();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_ERROR_ASSERTION,
                    "User sync failed oddly, IllegalStateException |" + e.getMessage());
            throw new UnknownSyncError();
        } catch (RecordTooLargeException e) {
            wipeLoginIfItOccurred();
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_ERROR_ASSERTION,
                    "Storage Full during user sync |" + e.getMessage());
            return new ResultAndError<>(PullTaskResult.STORAGE_FULL);
        }
    }

    private void handleLoginNeededOnSuccess() {
        if (loginNeeded) {
            // This is currently necessary to make sure that data is encoded, but there is
            // probably a better way to do it
            CommCareApplication.instance().startUserSession(
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
        this.publishProgress(PROGRESS_RECOVERY_NEEDED);
        Logger.log(LogTypes.TYPE_USER, "Sync Recovery Triggered");
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
            return new ResultAndError<>(PullTaskResult.RECOVERY_FAILURE, failureReason);
        } else {
            throw new UnknownSyncError();
        }
    }

    private void onSuccessfulSync() {
        recordSuccessfulSyncTime(username);

        ExternalDataUpdateHelper.broadcastDataUpdate(context, null);

        if (loginNeeded) {
            CommCareApplication.instance().getAppStorage(UserKeyRecord.class).write(ukrForLogin);
        }

        Logger.log(LogTypes.TYPE_USER, "User Sync Successful|" + username);
        updateCurrentUser(password);

        // Disable pending background syncs
        HiddenPreferences.clearPendingSyncRequest(username);

        this.publishProgress(PROGRESS_DONE);
    }

    private ResultAndError<PullTaskResult> handleServerError() {
        wipeLoginIfItOccurred();
        Logger.log(LogTypes.TYPE_USER, "500 Server Error during data pull|" + username);
        return new ResultAndError<>(PullTaskResult.SERVER_ERROR);
    }

    private ResultAndError<PullTaskResult> handleRateLimitedError() {
        wipeLoginIfItOccurred();
        Logger.log(LogTypes.TYPE_USER, "503 Server Error during data pull|" + username);
        return new ResultAndError<>(PullTaskResult.RATE_LIMITED_SERVER_ERROR);
    }

    private void wipeLoginIfItOccurred() {
        if (wasKeyLoggedIn) {
            CommCareApplication.instance().releaseUserResourcesAndServices();
        }
    }

    @Override
    public void tryAbort() {
        if (requestor != null) {
            requestor.abortCurrentRequest();
        }
    }

    private static void recordSuccessfulSyncTime(String username) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putLong(SyncDetailCalculations.getLastSyncKey(username), new Date().getTime()).apply();
    }

    //TODO: This and the normal sync share a ton of code. It's hard to really... figure out the right way to 
    private Pair<Integer, String> recover(CommcareRequestEndpoints requestor, AndroidTransactionParserFactory factory) {
        while (asyncRestoreHelper.retryWaitPeriodInProgress()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }

            if (isCancelled()) {
                return new Pair<>(PROGRESS_RECOVERY_FAIL_SAFE,
                        "Task was cancelled during recovery sync");
            }
        }

        // This chunk is the safe field of operations which can all fail in IO in such a way that
        // we can just report back that things didn't work and don't need to attempt any recovery
        // or additional work
        BitCache cache;
        try {
            // Make a new request without all of the flags
            RemoteDataPullResponse pullResponse =
                    dataPullRequester.makeDataPullRequest(this, requestor, server, false, skipFixtures);

            if (!(pullResponse.responseCode >= 200 && pullResponse.responseCode < 300)) {
                return new Pair<>(PROGRESS_RECOVERY_FAIL_SAFE,
                        "Received a non-success response during recovery sync");
            } else if (pullResponse.responseCode == 202) {
                ResultAndError<PullTaskResult> result =
                        asyncRestoreHelper.handleRetryResponseCode(pullResponse);
                if (PullTaskResult.RETRY_NEEDED.equals(result.data)) {
                    asyncRestoreHelper.startReportingServerProgress();
                    return recover(requestor, factory);
                } else {
                    return new Pair<>(PROGRESS_RECOVERY_FAIL_SAFE,
                            "Retry response during recovery sync was improperly formed");
                }
            }

            // Grab a cache. The plan is to download the incoming data, wipe (move) the existing
            // db, and then restore fresh from the downloaded file
            cache = pullResponse.writeResponseToCache(context);
        } catch (IOException e) {
            e.printStackTrace();
            //Ok, well, we're bailing here, but we didn't make any changes
            Logger.log(LogTypes.TYPE_USER, "Sync Recovery Failed due to IOException|" + e.getMessage());
            return new Pair<>(PROGRESS_RECOVERY_FAIL_SAFE, "");
        } catch (EncryptionKeyHelper.EncryptionKeyException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_USER, "Sync Recovery Failed due to Cache encryption error|" + e.getMessage());
            return new Pair<>(PROGRESS_RECOVERY_FAIL_SAFE, "");
        }

        this.publishProgress(PROGRESS_RECOVERY_STARTED);
        Logger.log(LogTypes.TYPE_USER, "Sync Recovery payload downloaded");

        //Ok. Here's where things get real. We now have a stable copy of the fresh data from the
        //server, so it's "safe" for us to wipe the casedb copy of it.

        //CTS: We're not doing this in a super good way right now, need to be way more fault tolerant.
        //this is the temporary implementation of everything past this point

        //Wipe storage
        SQLiteDatabase userDb = CommCareApplication.instance().getUserDbHandle();
        userDb.beginTransaction();
        wipeStorageForFourTwelveSync(userDb);

        try {
            String syncToken = readInputWithoutCommit(cache.retrieveCache(), factory);
            updateUserSyncToken(syncToken);
            Logger.log(LogTypes.TYPE_USER, "Sync Recovery Successful");
            userDb.setTransactionSuccessful();
            return new Pair<>(PROGRESS_DONE, "");
        } catch (InvalidStructureException | XmlPullParserException
                | UnfullfilledRequirementsException | SessionUnavailableException
                | IOException e) {
            Logger.exception("Sync recovery failed|" + e.getLocalizedMessage(), e);
            return new Pair<>(PROGRESS_RECOVERY_FAIL_BAD, e.getLocalizedMessage());
        } finally {
            userDb.endTransaction();
            //destroy temp file
            cache.release();
        }
    }

    private void wipeStorageForFourTwelveSync(SQLiteDatabase userDb) {
        SqlStorage.wipeTableWithoutCommit(userDb, ACase.STORAGE_KEY);
        SqlStorage.wipeTableWithoutCommit(userDb, Ledger.STORAGE_KEY);
        SqlStorage.wipeTableWithoutCommit(userDb, AndroidCaseIndexTable.TABLE_NAME);
        EntityStorageCache.wipeCacheForCurrentAppWithoutCommit(userDb);
    }

    private void updateCurrentUser(String password) {
        SqlStorage<User> storage = CommCareApplication.instance().getUserStorage("USER", User.class);
        User u = storage.getRecordForValue(User.META_USERNAME, username);
        CommCareApplication.instance().getSession().setCurrentUser(u, password);
    }

    private void updateUserSyncToken(String syncToken) {
        SqlStorage<User> storage = CommCareApplication.instance().getUserStorage("USER", User.class);
        try {
            User u = storage.getRecordForValue(User.META_USERNAME, username);
            u.setLastSyncToken(syncToken);
            storage.write(u);
        } catch (NoSuchElementException nsee) {
            //TODO: Something here? Maybe figure out if we downloaded a user from the server and attach the data to it?
        }
    }

    private void initParsers(AndroidTransactionParserFactory factory) {
        factory.initCaseParser();
        factory.initStockParser();
        Hashtable<String, String> formNamespaces = FormSaveUtil.getNamespaceToFilePathMap(CommCareApplication.instance().getAppStorage(FormDefRecord.class));
        factory.initFormInstanceParser(formNamespaces);
    }

    private void parseStream(InputStream stream,
                             AndroidTransactionParserFactory factory)
            throws InvalidStructureException, IOException, XmlPullParserException,
            UnfullfilledRequirementsException {
        DataModelPullParser parser = new DataModelPullParser(stream, factory, true, false, this);
        parser.parse();
    }

    private String readInputWithoutCommit(InputStream stream,
                                          AndroidTransactionParserFactory factory)
            throws InvalidStructureException, IOException, XmlPullParserException,
            UnfullfilledRequirementsException {
        initParsers(factory);
        parseStream(stream, factory);
        return factory.getSyncToken();
    }

    private String readInput(InputStream stream, AndroidTransactionParserFactory factory)
            throws InvalidStructureException, IOException, XmlPullParserException,
            UnfullfilledRequirementsException {
        initParsers(factory);
        //this is _really_ coupled, but we'll tolerate it for now because of the absurd performance gains
        SQLiteDatabase db = CommCareApplication.instance().getUserDbHandle();
        db.beginTransaction();
        try {
            parseStream(stream, factory);
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

    protected void reportServerProgress(int completedSoFar, int total) {
        publishProgress(PROGRESS_SERVER_PROCESSING, completedSoFar, total);
    }

    public void reportDownloadProgress(int totalRead) {
        publishProgress(PROGRESS_DOWNLOADING, totalRead);
    }

    public AsyncRestoreHelper getAsyncRestoreHelper() {
        return this.asyncRestoreHelper;
    }

    public enum PullTaskResult {
        DOWNLOAD_SUCCESS(AnalyticsParamValue.SYNC_SUCCESS),
        RETRY_NEEDED(AnalyticsParamValue.SYNC_FAIL_RETRY_NEEDED),
        EMPTY_URL(AnalyticsParamValue.SYNC_FAIL_EMPTY_URL),
        AUTH_FAILED(AnalyticsParamValue.SYNC_FAIL_AUTH),
        BAD_DATA(AnalyticsParamValue.SYNC_FAIL_BAD_DATA),
        BAD_DATA_REQUIRES_INTERVENTION(AnalyticsParamValue.SYNC_FAIL_BAD_DATA),
        UNKNOWN_FAILURE(AnalyticsParamValue.SYNC_FAIL_UNKNOWN),
        CANCELLED(AnalyticsParamValue.SYNC_FAIL_CANCELLED),
        ENCRYPTION_FAILURE(AnalyticsParamValue.SYNC_FAIL_ENCRYPTION),
        SESSION_EXPIRE(AnalyticsParamValue.SYNC_FAIL_SESSION_EXPIRE),
        RECOVERY_FAILURE(AnalyticsParamValue.SYNC_FAIL_RECOVERY),
        ACTIONABLE_FAILURE(AnalyticsParamValue.SYNC_FAIL_ACTIONABLE),
        UNREACHABLE_HOST(AnalyticsParamValue.SYNC_FAIL_UNREACHABLE_HOST),
        CONNECTION_TIMEOUT(AnalyticsParamValue.SYNC_FAIL_CONNECTION_TIMEOUT),
        SERVER_ERROR(AnalyticsParamValue.SYNC_FAIL_SERVER_ERROR),
        RATE_LIMITED_SERVER_ERROR(AnalyticsParamValue.SYNC_FAIL_RATE_LIMITED_SERVER_ERROR),
        STORAGE_FULL(AnalyticsParamValue.SYNC_FAIL_STORAGE_FULL),
        CAPTIVE_PORTAL(AnalyticsParamValue.SYNC_FAIL_CAPTIVE_PORTAL),
        AUTH_OVER_HTTP(AnalyticsParamValue.SYNC_FAIL_AUTH_OVER_HTTP),
        BAD_CERTIFICATE(AnalyticsParamValue.SYNC_FAIL_BAD_CERTIFICATE);

        public final String analyticsFailureReasonParam;

        PullTaskResult(String analyticsParam) {
            this.analyticsFailureReasonParam = analyticsParam;
        }
    }
}
