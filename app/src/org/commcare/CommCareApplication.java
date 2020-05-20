package org.commcare;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.os.StrictMode;
import android.text.format.DateUtils;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;

import org.commcare.activities.LoginActivity;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.CommCareNetworkService;
import org.commcare.core.network.CommCareNetworkServiceGenerator;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.core.services.CommCarePreferenceManagerFactory;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.engine.references.ArchiveFileRoot;
import org.commcare.engine.references.AssetFileRoot;
import org.commcare.engine.references.JavaHttpRoot;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.heartbeat.HeartbeatRequester;
import org.commcare.logging.AndroidLogger;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.logging.PreInitLogger;
import org.commcare.logging.XPathErrorEntry;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.logging.analytics.TimedStatsTracker;
import org.commcare.mediadownload.MissingMediaDownloadHelper;
import org.commcare.models.AndroidClassHasher;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.AndroidPrototypeFactorySetup;
import org.commcare.models.database.HybridFileBackedSqlHelpers;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.database.MigrationException;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.global.DatabaseGlobalOpenHelper;
import org.commcare.models.database.user.models.EntityStorageCache;
import org.commcare.models.legacy.LegacyInstallUtils;
import org.commcare.modern.database.Table;
import org.commcare.modern.util.PerformanceTuningUtil;
import org.commcare.network.DataPullRequester;
import org.commcare.network.DataPullResponseFactory;
import org.commcare.network.HttpUtils;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.LocalePreferences;
import org.commcare.services.CommCareSessionService;
import org.commcare.session.CommCareSession;
import org.commcare.sync.FormSubmissionHelper;
import org.commcare.sync.FormSubmissionWorker;
import org.commcare.tasks.DeleteLogs;
import org.commcare.tasks.LogSubmissionTask;
import org.commcare.tasks.PurgeStaleArchivedFormsTask;
import org.commcare.tasks.templates.ManagedAsyncTask;
import org.commcare.update.UpdateHelper;
import org.commcare.update.UpdateWorker;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCacheDirSetup;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.CommCareExceptionHandler;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.DeviceIdentifier;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.utils.PendingCalcs;
import org.commcare.utils.SessionActivityRegistration;
import org.commcare.utils.SessionStateUninitException;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.crypto.SecretKey;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;
import androidx.preference.PreferenceManager;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class CommCareApplication extends MultiDexApplication {

    private static final String TAG = CommCareApplication.class.getSimpleName();

    private static final int STATE_UNINSTALLED = 0;
    private static final int STATE_READY = 2;
    public static final int STATE_CORRUPTED = 4;
    public static final int STATE_LEGACY_DETECTED = 8;
    public static final int STATE_MIGRATION_FAILED = 16;
    public static final int STATE_MIGRATION_QUESTIONABLE = 32;
    private static final String DELETE_LOGS_REQUEST = "delete-logs-request";
    private static final long BACKOFF_DELAY_FOR_UPDATE_RETRY = 5 * 60 * 1000L; // 5 mins
    private static final long BACKOFF_DELAY_FOR_FORM_SUBMISSION_RETRY = 5 * 60 * 1000L; // 5 mins
    private static final long PERIODICITY_FOR_FORM_SUBMISSION_IN_HOURS = 1;


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

    private FirebaseAnalytics analyticsInstance;

    private String messageForUserOnDispatch;
    private String titleForUserMessage;

    // Indicates that a build refresh action has been triggered, but not yet completed
    private boolean latestBuildRefreshPending;

    private boolean invalidateCacheOnRestore;
    private CommCareNoficationManager noficationManager;

    @Override
    public void onCreate() {
        super.onCreate();

        turnOnStrictMode();

        CommCareApplication.app = this;
        CrashUtil.init(this);
        DataChangeLogger.init(this);
        logFirstCommCareRun();
        CommCarePreferenceManagerFactory.init(new AndroidPreferenceManager());

        configureCommCareEngineConstantsAndStaticRegistrations();
        initNotifications();

        //TODO: Make this robust
        PreInitLogger pil = new PreInitLogger();
        Logger.registerLogger(pil);

        // Workaround because android is written by 7 year-olds (re-uses http connection pool
        // improperly, so the second https request in a short time period will flop)
        System.setProperty("http.keepAlive", "false");

        Thread.setDefaultUncaughtExceptionHandler(new CommCareExceptionHandler(Thread.getDefaultUncaughtExceptionHandler(), this));

        SQLiteDatabase.loadLibs(this);

        setRoots();

        prepareTemporaryStorage();

        if (LegacyInstallUtils.checkForLegacyInstall(this)) {
            dbState = STATE_LEGACY_DETECTED;
        } else {
            // Init global storage (Just application records, logs, etc)
            dbState = initGlobalDb();
        }

        setupLoggerStorage(false);
        pil.dumpToNewLogger();

        initializeDefaultLocalizerData();

        if (dbState != STATE_MIGRATION_FAILED && dbState != STATE_MIGRATION_QUESTIONABLE) {
            AppUtils.checkForIncompletelyUninstalledApps();
            initializeAnAppOnStartup();
        }

        LocalePreferences.saveDeviceLocale(Locale.getDefault());
    }

    protected void turnOnStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocalePreferences.saveDeviceLocale(newConfig.locale);
    }

    private void initNotifications() {
        noficationManager = new CommCareNoficationManager(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            noficationManager.createNotificationChannels();
        }
    }

    private void logFirstCommCareRun() {
        if (isFirstRunAfterInstall()) {
            DataChangeLogger.log(new DataChangeLog.CommCareInstall());
        } else if (isFirstRunAfterUpdate()) {
            DataChangeLogger.log(new DataChangeLog.CommCareUpdate());
        }
    }

    // Whether user is running CommCare for the first time after installation
    public static boolean isFirstRunAfterInstall() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance());
        if (preferences.getBoolean(HiddenPreferences.FIRST_COMMCARE_RUN, true)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(HiddenPreferences.FIRST_COMMCARE_RUN, false);
            editor.putBoolean(ReportingUtils.getCommCareVersionString() + "-first-run", false);
            editor.apply();
            return true;
        }
        return false;
    }

    // Whether user is running CommCare for the first time after a CommCare update
    public static boolean isFirstRunAfterUpdate() {
        String prefKey = ReportingUtils.getCommCareVersionString() + "-first-run";
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance());
        if (preferences.getBoolean(prefKey, true)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(prefKey, false);
            editor.apply();
            return true;
        }
        return false;
    }

    /**
     * configure internal constants required (or available) for static behaviors in the CommCare
     * library to reflect the current platform capabilities
     */
    private void configureCommCareEngineConstantsAndStaticRegistrations() {
        // Sets the static strategy for the deserialization code to be based on an optimized
        // md5 hasher. Major speed improvements.
        AndroidClassHasher.registerAndroidClassHashStrategy();

        ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();

        PerformanceTuningUtil.updateMaxPrefetchCaseBlock(
                PerformanceTuningUtil.guessLargestSupportedBulkCaseFetchSizeFromHeap(memoryClass * 1024 * 1024));
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

            cancelWorkManagerTasks();

            releaseUserResourcesAndServices();

            // Switch loggers back over to using global storage, now that we don't have a session
            setupLoggerStorage(false);
        }
    }

    protected void cancelWorkManagerTasks() {
        // Cancel form Submissions for this user
        WorkManager.getInstance(this).cancelUniqueWork(FormSubmissionHelper.getFormSubmissionRequestName());
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

    synchronized public FirebaseAnalytics getAnalyticsInstance() {
        if (analyticsInstance == null) {
            analyticsInstance = FirebaseAnalytics.getInstance(this);
        }
        analyticsInstance.setUserId(getUserIdOrNull());
        return analyticsInstance;
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

    @NonNull
    public String getPhoneId() {
        /**
         * https://source.android.com/devices/tech/config/device-identifiers
         * https://issuetracker.google.com/issues/129583175#comment10
         * Starting from Android 10, apps cannot access non-resettable device ids unless they have special career permission.
         * If we still try to access it, SecurityException is thrown.
         */
        return DeviceIdentifier.getDeviceIdentifier(this);
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
            CrashUtil.reportException(e);
            resourceState = STATE_CORRUPTED;
            FirebaseAnalyticsUtil.reportCorruptAppState();
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
        // cancel all Workmanager tasks for the unseated record
        WorkManager.getInstance(CommCareApplication.instance())
                .cancelAllWorkByTag(record.getApplicationId());

        if (isSeated(record)) {
            this.currentApp.teardownSandbox();
            this.currentApp = null;
        }
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

    public String getUserIdOrNull() {
        try {
            return this.getSession().getLoggedInUser().getUniqueId();
        } catch (SessionUnavailableException e) {
            return null;
        }
    }

    public void prepareTemporaryStorage() {
        String tempRoot = this.getAndroidFsTemp();
        FileUtil.deleteFileOrDir(tempRoot);
        boolean success = FileUtil.createFolder(tempRoot);
        if (!success) {
            Logger.log(LogTypes.TYPE_ERROR_STORAGE, "Couldn't create temp folder");
        }

        String externalRoot = this.getAndroidFsExternalTemp();
        FileUtil.deleteFileOrDir(externalRoot);
        success = FileUtil.createFolder(externalRoot);
        if (!success) {
            Logger.log(LogTypes.TYPE_ERROR_STORAGE, "Couldn't create external file folder");
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

                        if (!HiddenPreferences.shouldDisableBackgroundWork()) {
                            scheduleAppUpdate();
                            scheduleFormSubmissions();
                        }

                        MissingMediaDownloadHelper.scheduleMissingMediaDownload();

                        doReportMaintenance();
                        mBoundService.initHeartbeatLifecycle();

                        // Register that this user was the last to successfully log in if it's a real user
                        if (!User.TYPE_DEMO.equals(user.getUserType())) {
                            getCurrentApp().getAppPreferences().edit().putString(
                                    HiddenPreferences.LAST_LOGGED_IN_USER, record.getUsername()).apply();

                            // clear any files orphaned by file-backed db transaction failures
                            HybridFileBackedSqlHelpers.removeOrphanedFiles(mBoundService.getUserDbHandle());

                            PurgeStaleArchivedFormsTask.launchPurgeTask();
                        }

                        if (EntityStorageCache.getEntityCacheWipedPref(user.getUniqueId()) < ReportingUtils.getAppVersion()) {
                            EntityStorageCache.wipeCacheForCurrentApp();
                        }

                        purgeLogs();

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

    private void purgeLogs() {
        if (shouldRunLogDeletion()) {
            OneTimeWorkRequest deleteLogsRequest = new OneTimeWorkRequest.Builder(DeleteLogs.class).build();
            WorkManager.getInstance(CommCareApplication.instance())
                    .enqueueUniqueWork(DELETE_LOGS_REQUEST, ExistingWorkPolicy.KEEP, deleteLogsRequest);
        }
    }

    private void scheduleFormSubmissions() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest formSubmissionRequest =
                new PeriodicWorkRequest.Builder(FormSubmissionWorker.class, PERIODICITY_FOR_FORM_SUBMISSION_IN_HOURS, TimeUnit.HOURS)
                        .addTag(getCurrentApp().getAppRecord().getApplicationId())
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                BACKOFF_DELAY_FOR_FORM_SUBMISSION_RETRY,
                                TimeUnit.MILLISECONDS)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                FormSubmissionHelper.getFormSubmissionRequestName(),
                ExistingPeriodicWorkPolicy.KEEP,
                formSubmissionRequest
        );
    }

    // Hand off an app update task to the Android WorkManager
    private void scheduleAppUpdate() {
        if (UpdateHelper.shouldAutoUpdate()) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build();


            PeriodicWorkRequest updateRequest =
                    new PeriodicWorkRequest.Builder(UpdateWorker.class, UpdateHelper.getAutoUpdatePeriodicity(), TimeUnit.HOURS)
                            .addTag(getCurrentApp().getAppRecord().getApplicationId())
                            .setConstraints(constraints)
                            .setBackoffCriteria(
                                    BackoffPolicy.EXPONENTIAL,
                                    BACKOFF_DELAY_FOR_UPDATE_RETRY,
                                    TimeUnit.MILLISECONDS)
                            .build();

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    UpdateHelper.getUpdateRequestName(),
                    ExistingPeriodicWorkPolicy.KEEP,
                    updateRequest
            );
        }
    }

    // check if it's been a week since last run
    private boolean shouldRunLogDeletion() {
        long lastLogDeletionRun = HiddenPreferences.getLastLogDeletionTime();
        long aWeekBeforeNow = new Date().getTime() - (DateUtils.DAY_IN_MILLIS * 7L);
        return new Date(lastLogDeletionRun).before(new Date(aWeekBeforeNow));
    }

    @SuppressLint("NewApi")
    private void doReportMaintenance() {
        // Create a new submission task no matter what. If nothing is pending, it'll see if there
        // are unsent reports and try to send them. Otherwise, it'll create the report
        SharedPreferences settings = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String url = LogSubmissionTask.getSubmissionUrl(settings);

        if (url == null) {
            Logger.log(LogTypes.TYPE_ERROR_ASSERTION, "PostURL isn't set. This should never happen");
            return;
        }
        CommCareUtil.executeLogSubmission(url, false);
    }

    /**
     * Whether automated stuff like auto-updates/syncing are valid and should
     * be triggered.
     */
    public static boolean areAutomatedActionsInvalid() {
        return isInDemoMode(true);
    }

    /**
     * Whether the current login is a "demo" mode login.
     *
     * Returns a provided default value if there is no active user login
     */
    public static boolean isInDemoMode(boolean defaultValue) {
        try {
            return User.TYPE_DEMO.equals(CommCareApplication.instance().getSession().getLoggedInUser().getUserType());
        } catch (SessionUnavailableException sue) {
            return defaultValue;
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

    public synchronized boolean isSyncPending() {
        if (areAutomatedActionsInvalid()) {
            return false;
        }

        return PendingCalcs.getPendingSyncStatus();
    }

    public boolean isPostUpdateSyncNeeded() {
        return getCurrentApp().getAppPreferences()
                .getBoolean(HiddenPreferences.POST_UPDATE_SYNC_NEEDED, false);
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
        doReportMaintenance();
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
        boolean loggingEnabled = HiddenPreferences.isLoggingEnabled();
        if (userStorageAvailable) {
            if (loggingEnabled) {
                Logger.registerLogger(new AndroidLogger(app.getUserStorage(AndroidLogEntry.STORAGE_KEY,
                        AndroidLogEntry.class)));
            } else {
                Logger.detachLogger();
            }
            ForceCloseLogger.registerStorage(app.getUserStorage(ForceCloseLogEntry.STORAGE_KEY,
                    ForceCloseLogEntry.class));
            XPathErrorLogger.registerStorage(app.getUserStorage(XPathErrorEntry.STORAGE_KEY,
                    XPathErrorEntry.class));
        } else {
            if (loggingEnabled) {
                Logger.registerLogger(new AndroidLogger(
                        app.getGlobalStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class)));
            } else {
                Logger.detachLogger();
            }
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

    public DataPullRequester getDataPullRequester() {
        return DataPullResponseFactory.INSTANCE;
    }

    public HeartbeatRequester getHeartbeatRequester() {
        return new HeartbeatRequester();
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


    public ModernHttpRequester createGetRequester(Context context, String url, Map<String, String> params,
                                                  HashMap headers, AuthInfo authInfo,
                                                  @Nullable HttpResponseProcessor responseProcessor) {
        return buildHttpRequester(context, url, params, headers, null, null,
                HTTPMethod.GET, authInfo, responseProcessor, true);
    }

    public ModernHttpRequester buildHttpRequester(Context context, String url,
                                                  Map<String, String> params,
                                                  HashMap headers, RequestBody requestBody,
                                                  List<MultipartBody.Part> parts,
                                                  HTTPMethod method,
                                                  AuthInfo authInfo,
                                                  @Nullable HttpResponseProcessor responseProcessor, boolean retry) {

        CommCareNetworkService networkService;
        if (authInfo instanceof AuthInfo.NoAuth) {
            networkService = CommCareNetworkServiceGenerator.createNoAuthCommCareNetworkService();
        } else {
            networkService = CommCareNetworkServiceGenerator.createCommCareNetworkService(
                    HttpUtils.getCredential(authInfo),
                    DeveloperPreferences.isEnforceSecureEndpointEnabled(), retry);
        }

        return new ModernHttpRequester(new AndroidCacheDirSetup(context),
                url,
                params,
                headers,
                requestBody,
                parts,
                networkService,
                method,
                responseProcessor);
    }

}
