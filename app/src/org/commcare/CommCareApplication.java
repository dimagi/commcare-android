package org.commcare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;

import org.acra.annotation.ReportsCrashes;
import org.commcare.activities.LoginActivity;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.network.ModernHttpRequester;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.engine.references.ArchiveFileRoot;
import org.commcare.engine.references.AssetFileRoot;
import org.commcare.engine.references.JavaHttpRoot;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.logging.AndroidLogger;
import org.commcare.logging.PreInitLogger;
import org.commcare.logging.XPathErrorEntry;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.google.services.analytics.GoogleAnalyticsUtils;
import org.commcare.logging.analytics.TimedStatsTracker;
import org.commcare.models.AndroidClassHasher;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.HybridFileBackedSqlHelpers;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.database.MigrationException;
import org.commcare.models.database.AndroidPrototypeFactorySetup;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.app.DatabaseAppOpenHelper;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.models.database.global.DatabaseGlobalOpenHelper;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.models.framework.Table;
import org.commcare.models.legacy.LegacyInstallUtils;
import org.commcare.modern.util.Pair;
import org.commcare.network.AndroidModernHttpRequester;
import org.commcare.network.DataPullRequester;
import org.commcare.network.DataPullResponseFactory;
import org.commcare.network.HttpUtils;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.provider.ProviderUtils;
import org.commcare.services.CommCareSessionService;
import org.commcare.session.CommCareSession;
import org.commcare.tasks.DataSubmissionListener;
import org.commcare.tasks.LogSubmissionTask;
import org.commcare.tasks.PurgeStaleArchivedFormsTask;
import org.commcare.tasks.UpdateTask;
import org.commcare.tasks.templates.ManagedAsyncTask;
import org.commcare.utils.ACRAUtil;
import org.commcare.utils.AndroidCacheDirSetup;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.CommCareExceptionHandler;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.utils.DummyPropertyManager;
import org.commcare.utils.SessionActivityRegistration;
import org.commcare.utils.SessionStateUninitException;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.PendingCalcs;
import org.javarosa.core.model.User;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.File;
import java.net.URL;
import java.util.HashMap;

import javax.crypto.SecretKey;

@ReportsCrashes(
        formUri = "https://your/cloudant/report",
        formUriBasicAuthLogin = "your_username",
        formUriBasicAuthPassword = "your_password",
        reportType = org.acra.sender.HttpSender.Type.JSON,
        httpMethod = org.acra.sender.HttpSender.Method.PUT)
public class CommCareApplication extends Application {

    private static final String TAG = CommCareApplication.class.getSimpleName();

    // Tracking ids for Google Analytics
    private static final String LIVE_TRACKING_ID = BuildConfig.ANALYTICS_TRACKING_ID_LIVE;
    private static final String DEV_TRACKING_ID = BuildConfig.ANALYTICS_TRACKING_ID_DEV;

    private static final int STATE_UNINSTALLED = 0;
    private static final int STATE_READY = 2;
    public static final int STATE_CORRUPTED = 4;
    public static final int STATE_MIGRATION_FAILED = 16;
    public static final int STATE_MIGRATION_QUESTIONABLE = 32;

    private int dbState;

    private static CommCareApplication app;

    private CommCareApp currentApp;

    // stores current state of application: the session, form
    private AndroidSessionWrapper sessionWrapper;

    private final Object globalDbHandleLock = new Object();
    private SQLiteDatabase globalDatabase;

    private ArchiveFileRoot mArchiveFileRoot;

    // A bound service is created out of the CommCareSessionService to ensure it stays in memory.
    private CommCareSessionService mBoundService;
    private ServiceConnection mConnection;
    private final Object serviceLock = new Object();
    private boolean sessionServiceIsBound = false;
    // Important so we don't use the service before the db is initialized.
    private boolean sessionServiceIsBinding = false;

    // Milliseconds to wait for bind
    private static final int MAX_BIND_TIMEOUT = 5000;

    private int mCurrentServiceBindTimeout = MAX_BIND_TIMEOUT;

    private GoogleAnalytics analyticsInstance;
    private Tracker analyticsTracker;

    private String messageForUserOnDispatch;
    private String titleForUserMessage;

    // Indicates that a build refresh action has been triggered, but not yet completed
    private boolean latestBuildRefreshPending;

    private boolean invalidateCacheOnRestore;
    private CommCareNoficationManager noficationManager;

    @Override
    public void onCreate() {
        super.onCreate();

        // Sets the static strategy for the deserialization code to be based on an optimized
        // md5 hasher. Major speed improvements.
        AndroidClassHasher.registerAndroidClassHashStrategy();

        CommCareApplication.app = this;
        noficationManager = new CommCareNoficationManager(this);

        //TODO: Make this robust
        PreInitLogger pil = new PreInitLogger();
        Logger.registerLogger(pil);

        // Workaround because android is written by 7 year-olds (re-uses http connection pool
        // improperly, so the second https request in a short time period will flop)
        System.setProperty("http.keepAlive", "false");

        Thread.setDefaultUncaughtExceptionHandler(new CommCareExceptionHandler(Thread.getDefaultUncaughtExceptionHandler(), this));

        PropertyManager.setPropertyManager(new DummyPropertyManager());

        SQLiteDatabase.loadLibs(this);

        setRoots();

        prepareTemporaryStorage();

        // Init global storage (Just application records, logs, etc)
        dbState = initGlobalDb();

        // This is where we go through and check for updates between major transitions.
        // Soon we should start doing this differently, and actually go to an activity
        // first which tells the user what's going on.
        // The rule about this transition is that if the user had logs pending, we still want
        // them in order, so we aren't going to dump our logs from the Pre-init logger until
        // after this transition occurs.
        try {
            LegacyInstallUtils.checkForLegacyInstall(this, this.getGlobalStorage(ApplicationRecord.class));
        } finally {
            // No matter what happens, set up our new logger, we want those logs!
            setupLoggerStorage(false);
            pil.dumpToNewLogger();
        }

        initializeDefaultLocalizerData();

        if (dbState != STATE_MIGRATION_FAILED && dbState != STATE_MIGRATION_QUESTIONABLE) {
            AppUtils.checkForIncompletelyUninstalledApps();
            initializeAnAppOnStartup();
        }

        ACRAUtil.initACRA(this);

        if (!GoogleAnalyticsUtils.versionIncompatible()) {
            analyticsInstance = GoogleAnalytics.getInstance(this);
            GoogleAnalyticsUtils.reportAndroidApiLevelAtStartup();
        }
    }

    public void startUserSession(byte[] symmetricKey, UserKeyRecord record, boolean restoreSession) {
        synchronized (serviceLock) {
            // if we already have a connection established to
            // CommCareSessionService, close it and open a new one
            SessionActivityRegistration.unregisterSessionExpiration();
            if (this.sessionServiceIsBound) {
                releaseUserResourcesAndServices();
            }
            bindUserSessionService(symmetricKey, record, restoreSession);
        }
    }

    /**
     * Closes down the user service, resources, and background tasks. Used for
     * manual user log-outs.
     */
    public void closeUserSession() {
        synchronized (serviceLock) {
            // Cancel any running tasks before closing down the user database.
            ManagedAsyncTask.cancelTasks();

            releaseUserResourcesAndServices();

            // Switch loggers back over to using global storage, now that we don't have a session
            setupLoggerStorage(false);
        }
    }

    /**
     * Closes down the user service, resources, and background tasks,
     * broadcasting an intent to redirect the user to the login screen. Used
     * for session-expiration related user logouts.
     */
    public void expireUserSession() {
        synchronized (serviceLock) {
            closeUserSession();
            SessionActivityRegistration.registerSessionExpiration();
            sendBroadcast(new Intent(SessionActivityRegistration.USER_SESSION_EXPIRED));
        }
    }

    public void releaseUserResourcesAndServices() {
        String userBeingLoggedOut = CommCareApplication.instance().getCurrentUserId();
        try {
            CommCareApplication.instance().getSession().closeServiceResources();
        } catch (SessionUnavailableException e) {
            Log.w(TAG, "User's session services have unexpectedly already " +
                    "been closed down. Proceeding to close the session.");
        }

        unbindUserSessionService();
        TimedStatsTracker.registerEndSession(userBeingLoggedOut);
    }

    public SecretKey createNewSymmetricKey() {
        return getSession().createNewSymmetricKey();
    }

    synchronized public Tracker getDefaultTracker() {
        if (analyticsTracker == null) {
            if (BuildConfig.DEBUG) {
                analyticsTracker = analyticsInstance.newTracker(DEV_TRACKING_ID);
            } else {
                analyticsTracker = analyticsInstance.newTracker(LIVE_TRACKING_ID);
            }
            analyticsTracker.enableAutoActivityTracking(true);
        }
        String userId = getCurrentUserId();
        if (!"".equals(userId)) {
            analyticsTracker.set("&uid", userId);
        } else {
            analyticsTracker.set("&uid", null);
        }
        return analyticsTracker;
    }

    public int[] getCommCareVersion() {
        return this.getResources().getIntArray(R.array.commcare_version);
    }

    public AndroidCommCarePlatform getCommCarePlatform() {
        if (this.currentApp == null) {
            throw new RuntimeException("No App installed!!!");
        } else {
            return this.currentApp.getCommCarePlatform();
        }
    }

    public CommCareApp getCurrentApp() {
        return this.currentApp;
    }

    /**
     * Get the current CommCare session that's being executed
     */
    public CommCareSession getCurrentSession() {
        return getCurrentSessionWrapper().getSession();
    }

    public AndroidSessionWrapper getCurrentSessionWrapper() {
        if (sessionWrapper == null) {
            throw new SessionStateUninitException("CommCare user session isn't available");
        }
        return sessionWrapper;
    }

    public int getDatabaseState() {
        return dbState;
    }

    public void initializeGlobalResources(CommCareApp app) {
        if (dbState != STATE_UNINSTALLED) {
            initializeAppResources(app);
        }
    }

    public @NonNull String getPhoneId() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_DENIED) {
            return "000000000000000";
        }

        TelephonyManager manager = (TelephonyManager)this.getSystemService(TELEPHONY_SERVICE);
        String imei = manager.getDeviceId();
        if (imei == null) {
            imei = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        }
        if (imei == null) {
            imei = "----";
        }
        return imei;
    }

    public void initializeDefaultLocalizerData() {
        Localization.init(true);
        Localization.registerLanguageReference("default",
                "jr://asset/locales/android_translatable_strings.txt");
        Localization.registerLanguageReference("default",
                "jr://asset/locales/android_startup_strings.txt");
        Localization.setDefaultLocale("default");

        // For now. Possibly handle this better in the future
        Localization.setLocale("default");
    }

    private void setRoots() {
        JavaHttpRoot http = new JavaHttpRoot();

        AssetFileRoot afr = new AssetFileRoot(this);

        ArchiveFileRoot arfr = new ArchiveFileRoot();

        mArchiveFileRoot = arfr;

        ReferenceManager.instance().addReferenceFactory(http);
        ReferenceManager.instance().addReferenceFactory(afr);
        ReferenceManager.instance().addReferenceFactory(arfr);
        ReferenceManager.instance().addRootTranslator(new RootTranslator("jr://media/",
                GlobalConstants.MEDIA_REF));
    }

    /**
     * Performs the appropriate initialization of an application when this CommCareApplication is
     * first launched
     */
    private void initializeAnAppOnStartup() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String lastAppId = prefs.getString(LoginActivity.KEY_LAST_APP, "");
        if (!"".equals(lastAppId)) {
            ApplicationRecord lastApp = MultipleAppsUtil.getAppById(lastAppId);
            if (lastApp == null || !lastApp.isUsable()) {
                AppUtils.initFirstUsableAppRecord();
            } else {
                initializeAppResources(new CommCareApp(lastApp));
            }
        } else {
            AppUtils.initFirstUsableAppRecord();
        }
    }

    /**
     * Initialize all of the given app's resources, and set the state of its resources accordingly
     *
     * @param app the CC app to initialize
     */
    public void initializeAppResources(CommCareApp app) {
        int resourceState;
        try {
            currentApp = app;

            if (currentApp.initializeApplication()) {
                resourceState = STATE_READY;
                this.sessionWrapper = new AndroidSessionWrapper(this.getCommCarePlatform());
            } else {
                resourceState = STATE_CORRUPTED;
            }
        } catch (Exception e) {
            Log.i("FAILURE", "Problem with loading");
            Log.i("FAILURE", "E: " + e.getMessage());
            e.printStackTrace();
            ForceCloseLogger.reportExceptionInBg(e);
            resourceState = STATE_CORRUPTED;
        }
        app.setAppResourceState(resourceState);
    }

    /**
     * @return if the given ApplicationRecord is the currently seated one
     */
    public boolean isSeated(ApplicationRecord record) {
        return currentApp != null && currentApp.getUniqueId().equals(record.getUniqueId());
    }

    /**
     * If the given record is the currently seated app, unseat it
     */
    public void unseat(ApplicationRecord record) {
        if (isSeated(record)) {
            this.currentApp.teardownSandbox();
            this.currentApp = null;
        }
    }

    /**
     * Completes a full uninstall of the CC app that the given ApplicationRecord represents.
     * This method should be idempotent and should be capable of completing an uninstall
     * regardless of previous failures
     */
    public void uninstall(ApplicationRecord record) {
        CommCareApp app = new CommCareApp(record);

        // 1) If the app we are uninstalling is the currently-seated app, tear down its sandbox
        if (isSeated(record)) {
            getCurrentApp().teardownSandbox();
            unseat(record);
        }

        // 2) Set record's status to delete requested, so we know if we have left it in a bad
        // state later
        record.setStatus(ApplicationRecord.STATUS_DELETE_REQUESTED);
        getGlobalStorage(ApplicationRecord.class).write(record);

        // 3) Delete the directory containing all of this app's resources
        if (!FileUtil.deleteFileOrDir(app.storageRoot())) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "App storage root was unable to be " +
                    "deleted during app uninstall. Aborting uninstall process for now.");
            return;
        }

        // 4) Delete all the user databases associated with this app
        SqlStorage<UserKeyRecord> userDatabase = app.getStorage(UserKeyRecord.class);
        for (UserKeyRecord user : userDatabase) {
            File f = getDatabasePath(DatabaseUserOpenHelper.getDbName(user.getUuid()));
            if (!FileUtil.deleteFileOrDir(f)) {
                Logger.log(AndroidLogger.TYPE_RESOURCES, "A user database was unable to be " +
                        "deleted during app uninstall. Aborting uninstall process for now.");
                // If we failed to delete a file, it is likely because there is an open pointer
                // to that db still in use, so stop the uninstall for now, and rely on it to
                // complete the next time the app starts up
                return;
            }
        }

        // 5) Delete the forms database for this app
        File formsDb = getDatabasePath(ProviderUtils.getProviderDbName(
                ProviderUtils.ProviderType.FORMS,
                app.getAppRecord().getApplicationId()));
        if (!FileUtil.deleteFileOrDir(formsDb)) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "The app's forms database was unable to be " +
                    "deleted during app uninstall. Aborting uninstall process for now.");
            return;
        }

        // 6) Delete the instances database for this app
        File instancesDb = getDatabasePath(ProviderUtils.getProviderDbName(
                ProviderUtils.ProviderType.INSTANCES,
                app.getAppRecord().getApplicationId()));
        if (!FileUtil.deleteFileOrDir(instancesDb)) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "The app's instances database was unable to" +
                    " be deleted during app uninstall. Aborting uninstall process for now.");
            return;
        }

        // 7) Delete the app database
        File f = getDatabasePath(DatabaseAppOpenHelper.getDbName(app.getAppRecord().getApplicationId()));
        if (!FileUtil.deleteFileOrDir(f)) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "The app database was unable to be deleted" +
                    "during app uninstall. Aborting uninstall process for now.");
            return;
        }

        // 8) Delete the ApplicationRecord
        getGlobalStorage(ApplicationRecord.class).remove(record.getID());
    }

    private int initGlobalDb() {
        SQLiteDatabase database;
        try {
            database = new DatabaseGlobalOpenHelper(this).getWritableDatabase("null");
            database.close();
            return STATE_READY;
        } catch (SQLiteException e) {
            // Only thrown if DB isn't there
            return STATE_UNINSTALLED;
        } catch (MigrationException e) {
            if (e.isDefiniteFailure()) {
                return STATE_MIGRATION_FAILED;
            } else {
                return STATE_MIGRATION_QUESTIONABLE;
            }
        }
    }

    public SQLiteDatabase getUserDbHandle() {
        return this.getSession().getUserDbHandle();
    }

    public <T extends Persistable> SqlStorage<T> getGlobalStorage(Class<T> c) {
        return getGlobalStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getGlobalStorage(String table, Class<T> c) {
        return new SqlStorage<>(table, c, new AndroidDbHelper(this.getApplicationContext()) {
            @Override
            public SQLiteDatabase getHandle() {
                synchronized (globalDbHandleLock) {
                    if (globalDatabase == null || !globalDatabase.isOpen()) {
                        globalDatabase = new DatabaseGlobalOpenHelper(this.c).getWritableDatabase("null");
                    }
                    return globalDatabase;
                }
            }
        });
    }

    public <T extends Persistable> SqlStorage<T> getAppStorage(Class<T> c) {
        return getAppStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getAppStorage(String name, Class<T> c) {
        return currentApp.getStorage(name, c);
    }

    public <T extends Persistable> HybridFileBackedSqlStorage<T> getFileBackedAppStorage(String name, Class<T> c) {
        return currentApp.getFileBackedStorage(name, c);
    }

    public <T extends Persistable> SqlStorage<T> getUserStorage(Class<T> c) {
        return getUserStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getUserStorage(String storage, Class<T> c) {
        return new SqlStorage<>(storage, c, buildUserDbHandle());
    }

    public <T extends Persistable> HybridFileBackedSqlStorage<T> getFileBackedUserStorage(String storage, Class<T> c) {
        return new HybridFileBackedSqlStorage<>(storage, c, buildUserDbHandle(),
                getUserKeyRecordId(), CommCareApplication.instance().getCurrentApp());
    }

    public String getUserKeyRecordId() {
        return getSession().getUserKeyRecordUUID();
    }

    protected AndroidDbHelper buildUserDbHandle() {
        return new AndroidDbHelper(this.getApplicationContext()) {
            @Override
            public SQLiteDatabase getHandle() {
                SQLiteDatabase database = getUserDbHandle();
                if (database == null) {
                    throw new SessionUnavailableException("The user database has been closed!");
                }
                return database;
            }
        };
    }

    public <T extends Persistable> SqlStorage<T> getRawStorage(String storage, Class<T> c, final SQLiteDatabase handle) {
        return new SqlStorage<>(storage, c, new AndroidDbHelper(this.getApplicationContext()) {
            @Override
            public SQLiteDatabase getHandle() {
                return handle;
            }
        });
    }

    public static CommCareApplication instance() {
        return app;
    }

    public String getCurrentUserId() {
        try {
            return this.getSession().getLoggedInUser().getUniqueId();
        } catch (SessionUnavailableException e) {
            return "";
        }
    }

    public void prepareTemporaryStorage() {
        String tempRoot = this.getAndroidFsTemp();
        FileUtil.deleteFileOrDir(tempRoot);
        boolean success = FileUtil.createFolder(tempRoot);
        if (!success) {
            Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "Couldn't create temp folder");
        }

        String externalRoot = this.getAndroidFsExternalTemp();
        FileUtil.deleteFileOrDir(externalRoot);
        success = FileUtil.createFolder(externalRoot);
        if (!success) {
            Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "Couldn't create external file folder");
        }

    }

    /**
     * Allows something within the current service binding to update the app to let it
     * know that the bind may take longer than the current timeout can allow
     */
    public void setCustomServiceBindTimeout(int timeout) {
        synchronized (serviceLock) {
            this.mCurrentServiceBindTimeout = timeout;
        }
    }

    private void bindUserSessionService(final byte[] key, final UserKeyRecord record,
                                        final boolean restoreSession) {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                // This is called when the connection with the service has been
                // established, giving us the service object we can use to
                // interact with the service.  Because we have bound to a explicit
                // service that we know is running in our own process, we can
                // cast its IBinder to a concrete class and directly access it.
                User user = null;
                synchronized (serviceLock) {
                    mCurrentServiceBindTimeout = MAX_BIND_TIMEOUT;

                    mBoundService = ((CommCareSessionService.LocalBinder)service).getService();

                    // Don't let anyone touch this until it's logged in
                    // Open user database
                    mBoundService.prepareStorage(key, record);

                    if (record != null) {
                        //Ok, so we have a login that was successful, but do we have a user model in the DB?
                        //We need to check before we're logged in, so we get the handle raw, here
                        for (User u : getRawStorage("USER", User.class, mBoundService.getUserDbHandle())) {
                            if (record.getUsername().equals(u.getUsername())) {
                                user = u;
                            }
                        }
                    }

                    // Switch all loggers over to using user storage while there is a session
                    setupLoggerStorage(true);

                    sessionServiceIsBound = true;

                    // Don't signal bind completion until the db is initialized.
                    sessionServiceIsBinding = false;

                    if (user != null) {
                        mBoundService.startSession(user, record);
                        if (restoreSession) {
                            CommCareApplication.this.sessionWrapper = DevSessionRestorer.restoreSessionFromPrefs(getCommCarePlatform());
                        } else {
                            CommCareApplication.this.sessionWrapper = new AndroidSessionWrapper(CommCareApplication.this.getCommCarePlatform());
                        }

                        if (shouldAutoUpdate()) {
                            startAutoUpdate();
                        }
                        syncPending = PendingCalcs.getPendingSyncStatus();

                        doReportMaintenance(false);
                        mBoundService.initHeartbeatLifecycle();

                        // Register that this user was the last to successfully log in if it's a real user
                        if (!User.TYPE_DEMO.equals(user.getUserType())) {
                            getCurrentApp().getAppPreferences().edit().putString(CommCarePreferences.LAST_LOGGED_IN_USER, record.getUsername()).commit();

                            // clear any files orphaned by file-backed db transaction failures
                            HybridFileBackedSqlHelpers.removeOrphanedFiles(mBoundService.getUserDbHandle());

                            PurgeStaleArchivedFormsTask.launchPurgeTask();
                        }
                    }

                    TimedStatsTracker.registerStartSession();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                // This is called when the connection with the service has been
                // unexpectedly disconnected -- that is, its process crashed.
                // Because it is running in our same process, we should never
                // see this happen.
                mBoundService = null;
            }
        };

        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        startService(new Intent(this, CommCareSessionService.class));
        bindService(new Intent(this, CommCareSessionService.class), mConnection, Context.BIND_AUTO_CREATE);
        sessionServiceIsBinding = true;
    }

    @SuppressLint("NewApi")
    private void doReportMaintenance(boolean force) {
        // Create a new submission task no matter what. If nothing is pending, it'll see if there
        // are unsent reports and try to send them. Otherwise, it'll create the report
        SharedPreferences settings = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String url = LogSubmissionTask.getSubmissionUrl(settings);

        if (url == null) {
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "PostURL isn't set. This should never happen");
            return;
        }

        DataSubmissionListener dataListener = getSession().getListenerForSubmissionNotification(R.string.submission_logs_title);

        LogSubmissionTask task = new LogSubmissionTask(
                force || PendingCalcs.isPending(settings.getLong(CommCarePreferences.LOG_LAST_DAILY_SUBMIT, 0), DateUtils.DAY_IN_MILLIS),
                dataListener,
                url);

        // Execute on a true multithreaded chain, since this is an asynchronous process
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
        }
    }

    /**
     * @return True if we aren't a demo user and the time to check for an
     * update has elapsed or we logged out while an auto-update was downlaoding
     * or queued for retry.
     */
    private static boolean shouldAutoUpdate() {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();

        return (!areAutomatedActionsInvalid() &&
                (ResourceInstallUtils.shouldAutoUpdateResume(currentApp) ||
                        PendingCalcs.isUpdatePending(currentApp.getAppPreferences())));
    }

    private static void startAutoUpdate() {
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Auto-Update Triggered");

        String ref = ResourceInstallUtils.getDefaultProfileRef();

        try {
            UpdateTask updateTask = UpdateTask.getNewInstance();
            updateTask.startPinnedNotification(CommCareApplication.instance());
            updateTask.setAsAutoUpdate();
            updateTask.executeParallel(ref);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Trying trigger auto-update when it is already running. " +
                    "Should only happen if the user triggered a manual update before this fired.");
        }
    }

    /**
     * Whether automated stuff like auto-updates/syncing are valid and should
     * be triggered.
     */
    private static boolean areAutomatedActionsInvalid() {
        try {
            return User.TYPE_DEMO.equals(CommCareApplication.instance().getSession().getLoggedInUser().getUserType());
        } catch (SessionUnavailableException sue) {
            return true;
        }
    }

    private void unbindUserSessionService() {
        synchronized (serviceLock) {
            if (sessionServiceIsBound) {
                if (sessionWrapper != null) {
                    sessionWrapper.reset();
                }
                sessionServiceIsBound = false;

                // Detach our existing connection.
                unbindService(mConnection);
                stopService(new Intent(this, CommCareSessionService.class));
            }
        }
    }

    public CommCareSessionService getSession() {
        long started = System.currentTimeMillis();
        while (sessionServiceIsBinding) {
            if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
                throw new SessionUnavailableException(
                        "Trying to access session on UI thread while session is binding");
            }
            if (System.currentTimeMillis() - started > mCurrentServiceBindTimeout) {
                // Something bad happened
                Log.e(TAG, "WARNING: Timed out while binding to session service, " +
                        "this may cause serious problems.");
                unbindUserSessionService();
                throw new SessionUnavailableException("Timeout binding to session service");
            }
        }

        if (sessionServiceIsBound) {
            synchronized (serviceLock) {
                return mBoundService;
            }
        } else {
            throw new SessionUnavailableException();
        }
    }

    public UserKeyRecord getRecordForCurrentUser() {
        return getSession().getUserKeyRecord();
    }

    private boolean syncPending = false;

    public synchronized boolean isSyncPending(boolean clearFlag) {
        if (areAutomatedActionsInvalid()) {
            return false;
        }
        // We only set this to true occasionally, but in theory it could be set to false
        // from other factors, so turn it off if it is.
        if (!PendingCalcs.getPendingSyncStatus()) {
            syncPending = false;
        }
        if (!syncPending) {
            return false;
        }
        if (clearFlag) {
            syncPending = false;
        }
        return true;
    }

    public boolean isStorageAvailable() {
        try {
            File storageRoot = new File(getAndroidFsRoot());
            return storageRoot.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Notify the application that something has occurred which has been
     * logged, and which should cause log submission to occur as soon as
     * possible.
     */
    public void notifyLogsPending() {
        doReportMaintenance(true);
    }

    public String getAndroidFsRoot() {
        return Environment.getExternalStorageDirectory().toString() + "/Android/data/" + getPackageName() + "/files/";
    }

    public String getAndroidFsTemp() {
        return Environment.getExternalStorageDirectory().toString() + "/Android/data/" + getPackageName() + "/temp/";
    }
    public String getAndroidFsExternalTemp() {
        return getAndroidFsRoot() + "/temp/external/";
    }

    /**
     * @return a path to a file location that can be used to store a file
     * temporarily and will be cleaned up as part of CommCare's application
     * lifecycle
     */
    public String getTempFilePath() {
        return getAndroidFsTemp() + PropertyUtils.genUUID();
    }

    /**
     * @param fileStem a relative file path to reference in the temp storage space
     * @return A file that can be shared with external applications via URI
     */
    public String getExternalTempPath(String fileStem) {
        return getAndroidFsExternalTemp() + fileStem;
    }

    public ArchiveFileRoot getArchiveFileRoot() {
        return mArchiveFileRoot;
    }

    /**
     * Used for manually linking to a session service during tests
     */
    public void setTestingService(CommCareSessionService service) {
        sessionServiceIsBound = true;
        mBoundService = service;
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
            }
        };
    }

    public void storeMessageForUserOnDispatch(String title, String message) {
        this.titleForUserMessage = title;
        this.messageForUserOnDispatch = message;
    }

    public String[] getPendingUserMessage() {
        if (messageForUserOnDispatch != null) {
            return new String[]{messageForUserOnDispatch, titleForUserMessage};
        }
        return null;
    }

    public void clearPendingUserMessage() {
        messageForUserOnDispatch = null;
        titleForUserMessage = null;
    }

    private static void setupLoggerStorage(boolean userStorageAvailable) {
        CommCareApplication app = CommCareApplication.instance();
        if (userStorageAvailable) {
            Logger.registerLogger(new AndroidLogger(app.getUserStorage(AndroidLogEntry.STORAGE_KEY,
                    AndroidLogEntry.class)));
            ForceCloseLogger.registerStorage(app.getUserStorage(ForceCloseLogEntry.STORAGE_KEY,
                    ForceCloseLogEntry.class));
            XPathErrorLogger.registerStorage(app.getUserStorage(XPathErrorEntry.STORAGE_KEY,
                    XPathErrorEntry.class));
        } else {
            Logger.registerLogger(new AndroidLogger(
                    app.getGlobalStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class)));
            ForceCloseLogger.registerStorage(
                    app.getGlobalStorage(ForceCloseLogEntry.STORAGE_KEY, ForceCloseLogEntry.class));
        }
    }

    public void setPendingRefreshToLatestBuild(boolean b) {
        this.latestBuildRefreshPending = b;
    }

    public boolean checkPendingBuildRefresh() {
        if (this.latestBuildRefreshPending) {
            this.latestBuildRefreshPending = false;
            return true;
        }
        return false;
    }

    public ModernHttpRequester buildHttpRequesterForLoggedInUser(Context context, URL url,
                                                                 HashMap<String, String> params,
                                                                 boolean isAuthenticatedRequest,
                                                                 boolean isPostRequest) {
        Pair<User, String> userAndDomain =
                HttpUtils.getUserAndDomain(isAuthenticatedRequest);
        return new AndroidModernHttpRequester(new AndroidCacheDirSetup(context), url, params,
                userAndDomain.first, userAndDomain.second, isAuthenticatedRequest, isPostRequest);
    }

    public DataPullRequester getDataPullRequester() {
        return DataPullResponseFactory.INSTANCE;
    }

    /**
     * A consumer app is a CommCare build flavor in which the .ccz and restore file for a specific
     * app and user have been pre-packaged along with CommCare into a custom .apk, and placed on
     * the Play Store under a custom name/branding scheme.
     */
    public boolean isConsumerApp() {
        return BuildConfig.IS_CONSUMER_APP;
    }

    public boolean shouldInvalidateCacheOnRestore() {
        return invalidateCacheOnRestore;
    }

    public void setInvalidateCacheFlag(boolean b) {
        invalidateCacheOnRestore = b;
    }

    public PrototypeFactory getPrototypeFactory(Context c) {
        return AndroidPrototypeFactorySetup.getPrototypeFactory(c);
    }

    public static CommCareNoficationManager notificationManager() {
        return app.noficationManager;
    }

}
