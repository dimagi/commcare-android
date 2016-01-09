package org.commcare.dalvik.application;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;

import org.acra.annotation.ReportsCrashes;
import org.commcare.android.analytics.TimedStatsTracker;
import org.commcare.android.database.AndroidDbHelper;
import org.commcare.android.database.MigrationException;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.DatabaseAppOpenHelper;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.DatabaseGlobalOpenHelper;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.CommCareUserOpenHelper;
import org.commcare.android.db.legacy.LegacyInstallUtils;
import org.commcare.android.framework.SessionActivityRegistration;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.javarosa.PreInitLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.notifications.NotificationClearReceiver;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.references.ArchiveFileRoot;
import org.commcare.android.references.AssetFileRoot;
import org.commcare.android.references.JavaHttpRoot;
import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.session.DevSessionRestorer;
import org.commcare.android.storage.framework.Table;
import org.commcare.android.tasks.DataSubmissionListener;
import org.commcare.android.tasks.ExceptionReporting;
import org.commcare.android.tasks.LogSubmissionTask;
import org.commcare.android.tasks.PurgeStaleArchivedFormsTask;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.android.tasks.templates.ManagedAsyncTask;
import org.commcare.android.util.ACRAUtil;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidUtil;
import org.commcare.android.util.CommCareExceptionHandler;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.ODKPropertyManager;
import org.commcare.android.util.SessionStateUninitException;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.DispatchActivity;
import org.commcare.dalvik.activities.LoginActivity;
import org.commcare.dalvik.activities.MessageActivity;
import org.commcare.dalvik.activities.UnrecoverableErrorActivity;
import org.commcare.dalvik.odk.provider.ProviderUtils;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.services.CommCareSessionService;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Profile;
import org.commcare.util.externalizable.AndroidClassHasher;
import org.javarosa.core.model.User;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.PropertyUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import javax.crypto.SecretKey;

/**
 * @author ctsims
 */
@ReportsCrashes(
        formUri = "https://your/cloudant/report",
        formUriBasicAuthLogin = "your_username",
        formUriBasicAuthPassword = "your_password",
        reportType = org.acra.sender.HttpSender.Type.JSON,
        httpMethod = org.acra.sender.HttpSender.Method.PUT)
public class CommCareApplication extends Application {

    private static final String TAG = CommCareApplication.class.getSimpleName();

    // Tracking ids for Google Analytics
    private static final String LIVE_TRACKING_ID = "UA-69708208-1";
    private static final String DEV_TRACKING_ID = "UA-69708208-2";

    private static final int STATE_UNINSTALLED = 0;
    public static final int STATE_UPGRADE = 1;
    private static final int STATE_READY = 2;
    public static final int STATE_CORRUPTED = 4;
    public static final int STATE_DELETE_REQUESTED = 8;
    public static final int STATE_MIGRATION_FAILED = 16;
    public static final int STATE_MIGRATION_QUESTIONABLE = 32;

    private static final String ACTION_PURGE_NOTIFICATIONS = "CommCareApplication_purge";

    private int dbState;

    private static CommCareApplication app;

    private CommCareApp currentApp;

    // stores current state of application: the session, form
    private AndroidSessionWrapper sessionWrapper;

    private final Object globalDbHandleLock = new Object();
    private SQLiteDatabase globalDatabase;

    private ArchiveFileRoot mArchiveFileRoot;

    // Fields for managing a connection to the CommCareSessionService
    //
    // A bound service is created out of the CommCareSessionService to ensure
    // it stays in memory.
    private CommCareSessionService mBoundService;
    private ServiceConnection mConnection;
    private final Object serviceLock = new Object();
    // Has the CommCareSessionService been bound?
    private boolean mIsBound = false;
    // Has CommCareSessionService initilization finished?
    // Important so we don't use the service before the db is initialized.
    private boolean mIsBinding = false;

    //Milliseconds to wait for bind
    private static final int MAX_BIND_TIMEOUT = 5000;

    private int mCurrentServiceBindTimeout = MAX_BIND_TIMEOUT;

    /**
     * Handler to receive notifications and show them the user using toast.
     */
    private final PopupHandler toaster = new PopupHandler(this);

    private GoogleAnalytics analyticsInstance;
    private Tracker analyticsTracker;
    private String currentUserId;

    @Override
    public void onCreate() {
        super.onCreate();

        //Sets the static strategy for the deserializtion code to be
        //based on an optimized md5 hasher. Major speed improvements.
        AndroidClassHasher.registerAndroidClassHashStrategy();
        AndroidUtil.initializeStaticHandlers();

        CommCareApplication.app = this;

        //TODO: Make this robust
        PreInitLogger pil = new PreInitLogger();
        Logger.registerLogger(pil);

        //Workaround because android is written by 7 year olds.
        //(reuses http connection pool improperly, so the second https
        //request in a short time period will flop)
        System.setProperty("http.keepAlive", "false");

        Thread.setDefaultUncaughtExceptionHandler(new CommCareExceptionHandler(Thread.getDefaultUncaughtExceptionHandler(), this));

        PropertyManager.setPropertyManager(new ODKPropertyManager());

        SQLiteDatabase.loadLibs(this);

        setRoots();

        prepareTemporaryStorage();

        //Init global storage (Just application records, logs, etc)
        dbState = initGlobalDb();

        //This is where we go through and check for updates between major transitions.
        //Soon we should start doing this differently, and actually go to an activity
        //first which tells the user what's going on.
        //
        //The rule about this transition is that if the user had logs pending, we still want them in order, so
        //we aren't going to dump our logs from the Pre-init logger until after this transition occurs.
        try {
            LegacyInstallUtils.checkForLegacyInstall(this, this.getGlobalStorage(ApplicationRecord.class));
        } catch (SessionUnavailableException sfe) {
            throw new RuntimeException(sfe);
        } finally {
            //No matter what happens, set up our new logger, we want those logs!
            Logger.registerLogger(new AndroidLogger(this.getGlobalStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class)));
            pil.dumpToNewLogger();
        }

        intializeDefaultLocalizerData();

        if (dbState != STATE_MIGRATION_FAILED && dbState != STATE_MIGRATION_QUESTIONABLE) {
            initializeAnAppOnStartup();
        }

        ACRAUtil.initACRA(this);
        analyticsInstance = GoogleAnalytics.getInstance(this);
    }

    public void triggerHandledAppExit(Context c, String message) {
        triggerHandledAppExit(c, message, Localization.get("app.handled.error.title"));
    }

    public void triggerHandledAppExit(Context c, String message, String title) {
        triggerHandledAppExit(c, message, title, true);
    }

    public void triggerHandledAppExit(Context c, String message, String title,
                                      boolean useExtraMessage) {
        Intent i = new Intent(c, UnrecoverableErrorActivity.class);
        i.putExtra(UnrecoverableErrorActivity.EXTRA_ERROR_TITLE, title);
        i.putExtra(UnrecoverableErrorActivity.EXTRA_ERROR_MESSAGE, message);
        i.putExtra(UnrecoverableErrorActivity.EXTRA_USE_MESSAGE, useExtraMessage);

        // start a new stack and forget where we were (so we don't restart the app from there)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        c.startActivity(i);
    }

    public static void restartCommCare(Activity activity) {
        Intent intent = new Intent(activity, DispatchActivity.class);

        // Make sure that the new stack starts with a dispatch activity, and clear everything
        // between.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        }

        activity.moveTaskToBack(true);
        activity.startActivity(intent);
        activity.finish();

        System.exit(0);
    }

    public void startUserSession(byte[] symetricKey, UserKeyRecord record, boolean restoreSession) {
        synchronized (serviceLock) {
            // if we already have a connection established to
            // CommCareSessionService, close it and open a new one
            if (this.mIsBound) {
                releaseUserResourcesAndServices();
            }
            bindUserSessionService(symetricKey, record, restoreSession);
        }
    }

    /**
     * Closes down the user service, resources, and background tasks. Used for
     * manual user log-outs.
     */
    public void closeUserSession() {
        synchronized (serviceLock) {
            // Cancel any running tasks before closing down the user databse.
            ManagedAsyncTask.cancelTasks();

            releaseUserResourcesAndServices();
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
        String userBeingLoggedOut = CommCareApplication._().getCurrentUserId();
        try {
            CommCareApplication._().getSession().closeServiceResources();
        } catch (SessionUnavailableException e) {
            Log.w(TAG, "User's session services have unexpectedly already " +
                    "been closed down. Proceeding to close the session.");
        }

        unbindUserSessionService();
        TimedStatsTracker.registerEndSession(userBeingLoggedOut);
    }

    public SecretKey createNewSymetricKey() throws SessionUnavailableException {
        return getSession().createNewSymetricKey();
    }

    synchronized public Tracker getDefaultTracker() {
        if (analyticsTracker == null) {
            // TODO: AMS - Will want to set this conditionally after test release
            analyticsTracker = analyticsInstance.newTracker(DEV_TRACKING_ID);
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

    public GoogleAnalytics getAnalyticsInstance() {
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

    public String getPhoneId() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_DENIED) {
            return "000000000000000";
        }

        TelephonyManager manager = (TelephonyManager)this.getSystemService(TELEPHONY_SERVICE);
        String imei = manager.getDeviceId();
        if (imei == null) {
            imei = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        }
        return imei;
    }

    public void intializeDefaultLocalizerData() {
        Localization.init(true);
        Localization.registerLanguageReference("default",
                "jr://asset/locales/messages_ccodk_default.txt");
        Localization.setDefaultLocale("default");

        //For now. Possibly handle this better in the future
        Localization.setLocale("default");
    }

    private void setRoots() {
        JavaHttpRoot http = new JavaHttpRoot();

        AssetFileRoot afr = new AssetFileRoot(this);

        ArchiveFileRoot arfr = new ArchiveFileRoot();

        mArchiveFileRoot = arfr;

        ReferenceManager._().addReferenceFactory(http);
        ReferenceManager._().addReferenceFactory(afr);
        ReferenceManager._().addReferenceFactory(arfr);
        ReferenceManager._().addRootTranslator(new RootTranslator("jr://media/",
                GlobalConstants.MEDIA_REF));
    }

    /**
     * Performs the appropriate initialization of an application when this CommCareApplication is
     * first launched
     */
    private void initializeAnAppOnStartup() {
        // Before we try to initialize a new app, check if any existing apps were left in a
        // partially deleted state, and finish uninstalling them if so
        for (ApplicationRecord record : getGlobalStorage(ApplicationRecord.class)) {
            if (record.getStatus() == ApplicationRecord.STATUS_DELETE_REQUESTED) {
                try {
                    uninstall(record);
                } catch (RuntimeException e) {
                    Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "Unable to uninstall an app " +
                            "during startup that was previously left partially-deleted");
                }
            }
        }

        // There may now be multiple app records in storage, because of multiple apps support. We
        // want to initialize one of them to start, so that there will be currently-seated app when
        // the login screen starts up

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String lastAppId = prefs.getString(LoginActivity.KEY_LAST_APP, "");
        if (!"".equals(lastAppId)) {
            // If there is a 'last app' set in shared preferences, try to initialize that application.
            ApplicationRecord lastApp = getAppById(lastAppId);
            if (lastApp == null || !lastApp.isUsable()) {
                // This app record could be null if it has since been uninstalled, or unusable if
                // it has since been archived, etc. In either case, just revert to picking the
                // first app
                initFirstUsableAppRecord();
            } else {
                initializeAppResources(new CommCareApp(lastApp));
            }
        } else {
            // Otherwise, just pick the first app in the list to initialize
            initFirstUsableAppRecord();
        }
    }

    /**
     * Initializes the first "usable" application from the list of globally installed app records,
     * if there is one
     */
    public void initFirstUsableAppRecord() {
        for (ApplicationRecord record : getUsableAppRecords()) {
            initializeAppResources(new CommCareApp(record));
            break;
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
                //????
                resourceState = STATE_CORRUPTED;
            }
        } catch (Exception e) {
            Log.i("FAILURE", "Problem with loading");
            Log.i("FAILURE", "E: " + e.getMessage());
            e.printStackTrace();
            ExceptionReporting.reportExceptionInBg(e);
            resourceState = STATE_CORRUPTED;
        }
        app.setAppResourceState(resourceState);
    }

    /**
     * @return all ApplicationRecords in storage, regardless of their status, in alphabetical order
     */
    public ArrayList<ApplicationRecord> getInstalledAppRecords() {
        ArrayList<ApplicationRecord> records = new ArrayList<>();
        for (ApplicationRecord r : getGlobalStorage(ApplicationRecord.class)) {
            records.add(r);
        }
        Collections.sort(records, new Comparator<ApplicationRecord>() {

            @Override
            public int compare(ApplicationRecord lhs, ApplicationRecord rhs) {
                return lhs.getDisplayName().compareTo(rhs.getDisplayName());
            }

        });
        return records;
    }

    /**
     * @return all ApplicationRecords that have status installed and are NOT archived
     */
    private ArrayList<ApplicationRecord> getVisibleAppRecords() {
        ArrayList<ApplicationRecord> visible = new ArrayList<>();
        for (ApplicationRecord r : getInstalledAppRecords()) {
            if (r.isVisible()) {
                visible.add(r);
            }
        }
        return visible;
    }

    /**
     * @return all ApplicationRecords that are installed AND are not archived AND have MM verified
     */
    public ArrayList<ApplicationRecord> getUsableAppRecords() {
        ArrayList<ApplicationRecord> ready = new ArrayList<>();
        for (ApplicationRecord r : getInstalledAppRecords()) {
            if (r.isUsable()) {
                ready.add(r);
            }
        }
        return ready;
    }

    /**
     * @return whether the user should be sent to CommCareVerificationActivity. Current logic is
     * that this should occur only if there is exactly one visible app and it is missing its MM
     * (because we are then assuming the user is not currently using multiple apps functionality)
     */
    public boolean shouldSeeMMVerification() {
        return (CommCareApplication._().getVisibleAppRecords().size() == 1 &&
                CommCareApplication._().getUsableAppRecords().size() == 0);
    }

    public boolean usableAppsPresent() {
        return getUsableAppRecords().size() > 0;
    }

    /**
     * @return the list of all installed apps as an array
     */
    public ApplicationRecord[] appRecordArray() {
        ArrayList<ApplicationRecord> appList = CommCareApplication._().getInstalledAppRecords();
        ApplicationRecord[] appArray = new ApplicationRecord[appList.size()];
        int index = 0;
        for (ApplicationRecord r : appList) {
            appArray[index++] = r;
        }
        return appArray;
    }

    /**
     * @param uniqueId - the uniqueId of the ApplicationRecord being sought
     * @return the ApplicationRecord corresponding to the given id, if it exists. Otherwise,
     * return null
     */
    public ApplicationRecord getAppById(String uniqueId) {
        for (ApplicationRecord r : getInstalledAppRecords()) {
            if (r.getUniqueId().equals(uniqueId)) {
                return r;
            }
        }
        return null;
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
            File f = getDatabasePath(CommCareUserOpenHelper.getDbName(user.getUuid()));
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
            //Only thrown if DB isn't there
            return STATE_UNINSTALLED;
        } catch (MigrationException e) {
            if (e.isDefiniteFailure()) {
                return STATE_MIGRATION_FAILED;
            } else {
                return STATE_MIGRATION_QUESTIONABLE;
            }
        }
    }

    public SQLiteDatabase getUserDbHandle() throws SessionUnavailableException {
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

    public <T extends Persistable> SqlStorage<T> getUserStorage(Class<T> c) {
        return getUserStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getUserStorage(String storage, Class<T> c) {
        return new SqlStorage<>(storage, c, new AndroidDbHelper(this.getApplicationContext()) {
            @Override
            public SQLiteDatabase getHandle() throws SessionUnavailableException {
                SQLiteDatabase database = getUserDbHandle();
                if (database == null) {
                    throw new SessionUnavailableException("The user database has been closed!");
                }
                return database;
            }
        });
    }

    public <T extends Persistable> SqlStorage<T> getRawStorage(String storage, Class<T> c, final SQLiteDatabase handle) {
        return new SqlStorage<>(storage, c, new AndroidDbHelper(this.getApplicationContext()) {
            @Override
            public SQLiteDatabase getHandle() {
                return handle;
            }
        });
    }

    public static CommCareApplication _() {
        return app;
    }

    /**
     * This method wipes out all local user data (users, referrals, etc) but leaves
     * application resources in place.
     *
     * It makes no attempt to make sure this is a safe operation when called, so
     * it shouldn't be used lightly.
     */
    public void clearUserData() {
//        //First clear anything that will require the user's key, since we're going to wipe it out!
//        getStorage(ACase.STORAGE_KEY, ACase.class).removeAll();
//
//        //TODO: We should really be wiping out the _stored_ instances here, too
//        getStorage(FormRecord.STORAGE_KEY, FormRecord.class).removeAll();
//
//        //Also, any of the sessions we've got saved
//        getStorage(SessionStateDescriptor.STORAGE_KEY, SessionStateDescriptor.class).removeAll();
//
//        //Now we wipe out the user entirely
//        getStorage(User.STORAGE_KEY, User.class).removeAll();
//
//        //Get rid of any user fixtures
//        getStorage("fixture", FormInstance.class).removeAll();
//
//        getStorage(GeocodeCacheModel.STORAGE_KEY, GeocodeCacheModel.class).removeAll();

        final String username;
        try {
            username = this.getSession().getLoggedInUser().getUsername();
        } catch (SessionUnavailableException e) {
            return;
        }

        final Set<String> dbIdsToRemove = new HashSet<>();

        this.getAppStorage(UserKeyRecord.class).removeAll(new EntityFilter<UserKeyRecord>() {

            @Override
            public boolean matches(UserKeyRecord ukr) {
                if (ukr.getUsername().equalsIgnoreCase(username.toLowerCase())) {
                    dbIdsToRemove.add(ukr.getUuid());
                    return true;
                }
                return false;
            }
        });

        //TODO: We can just delete the db entirely.

        Editor sharedPreferencesEditor = CommCareApplication._().getCurrentApp().getAppPreferences().edit();

        sharedPreferencesEditor.putString(CommCarePreferences.LAST_LOGGED_IN_USER, null);

        sharedPreferencesEditor.commit();

        for (String id : dbIdsToRemove) {
            //TODO: We only wanna do this if the user is the _last_ one with a key to this id, actually.
            //(Eventually)
            this.getDatabasePath(CommCareUserOpenHelper.getDbName(id)).delete();
        }
        CommCareApplication._().closeUserSession();
    }

    public String getCurrentUserId() {
        if (currentUserId != null) {
            return currentUserId;
        }
        try {
            currentUserId = this.getSession().getLoggedInUser().getUniqueId();
            return currentUserId;
        } catch (SessionUnavailableException e) {
            return "";
        }
    }

    private void refreshUserIdCache() {
        try {
            currentUserId = this.getSession().getLoggedInUser().getUniqueId();
        } catch (SessionUnavailableException e) {
        }
    }

    public void prepareTemporaryStorage() {
        String tempRoot = this.getAndroidFsTemp();
        FileUtil.deleteFileOrDir(tempRoot);
        boolean success = FileUtil.createFolder(tempRoot);
        if (!success) {
            Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "Couldn't create temp folder");
        }
    }

    public String getCurrentVersionString() {
        PackageManager pm = this.getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return "ERROR! Incorrect package version requested";
        }
        int[] versions = this.getCommCareVersion();
        String ccv = "";
        for (int vn : versions) {
            if (!"".equals(ccv)) {
                ccv += ".";
            }
            ccv += vn;
        }


        String profileVersion = "";

        Profile p = this.currentApp == null ? null : this.getCommCarePlatform().getCurrentProfile();
        if (p != null) {
            profileVersion = String.valueOf(p.getVersion());
        }


        String buildDate = BuildConfig.BUILD_DATE;
        String buildNumber = BuildConfig.BUILD_NUMBER;

        return Localization.get(getString(R.string.app_version_string), new String[]{pi.versionName, String.valueOf(pi.versionCode), ccv, buildNumber, buildDate, profileVersion});
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

                    //Don't let anyone touch this until it's logged in
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

                    //service available
                    mIsBound = true;

                    //Don't signal bind completion until the db is initialized.
                    mIsBinding = false;

                    if (user != null) {
                        mBoundService.startSession(user);
                        if (restoreSession) {
                            CommCareApplication.this.sessionWrapper = DevSessionRestorer.restoreSessionFromPrefs(getCommCarePlatform());
                        } else {
                            CommCareApplication.this.sessionWrapper = new AndroidSessionWrapper(CommCareApplication.this.getCommCarePlatform());
                        }

                        if (shouldAutoUpdate()) {
                            startAutoUpdate();
                        }
                        syncPending = getPendingSyncStatus();

                        doReportMaintenance(false);

                        //Register that this user was the last to successfully log in if it's a real user
                        if (!User.TYPE_DEMO.equals(user.getUserType())) {
                            getCurrentApp().getAppPreferences().edit().putString(CommCarePreferences.LAST_LOGGED_IN_USER, record.getUsername()).commit();

                            PurgeStaleArchivedFormsTask.launchPurgeTask();
                        }
                    }

                    refreshUserIdCache();
                    TimedStatsTracker.registerStartSession();
                }
            }


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
        mIsBinding = true;
    }

    @SuppressLint("NewApi")
    private void doReportMaintenance(boolean force) {
        //OK. So for now we're going to daily report sends and not bother with any of the frequency properties.


        //Create a new submission task no matter what. If nothing is pending, it'll see if there are unsent reports
        //and try to send them. Otherwise, it'll create the report
        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        String url = settings.getString(CommCarePreferences.PREFS_SUBMISSION_URL_KEY, null);

        if (url == null) {
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "PostURL isn't set. This should never happen");
            return;
        }

        DataSubmissionListener dataListener;

        try {
            dataListener =
                    CommCareApplication.this.getSession().startDataSubmissionListener(R.string.submission_logs_title);
        } catch (SessionUnavailableException sue) {
            // abort since it looks like the session expired
            return;
        }

        LogSubmissionTask task = new LogSubmissionTask(
                force || isPending(settings.getLong(CommCarePreferences.LOG_LAST_DAILY_SUBMIT, 0), DateUtils.DAY_IN_MILLIS),
                dataListener,
                url);

        //Execute on a true multithreaded chain, since this is an asynchronous process
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
    private boolean shouldAutoUpdate() {
        return (!areAutomatedActionsInvalid() &&
                (ResourceInstallUtils.shouldAutoUpdateResume(getCurrentApp()) ||
                        isUpdatePending()));
    }

    private void startAutoUpdate() {
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Auto-Update Triggered");

        String ref = ResourceInstallUtils.getDefaultProfileRef();

        try {
            UpdateTask updateTask = UpdateTask.getNewInstance();
            updateTask.startPinnedNotification(this);
            updateTask.setAsAutoUpdate();
            updateTask.execute(ref);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Trying trigger auto-update when it is already running. " +
                    "Should only happen if the user triggered a manual update before this fired.");
        }
    }

    public boolean isUpdatePending() {
        SharedPreferences preferences = getCurrentApp().getAppPreferences();
        //Establish whether or not an AutoUpdate is Pending
        String autoUpdateFreq =
                preferences.getString(CommCarePreferences.AUTO_UPDATE_FREQUENCY,
                        CommCarePreferences.FREQUENCY_NEVER);

        //See if auto update is even turned on
        if (!autoUpdateFreq.equals(CommCarePreferences.FREQUENCY_NEVER)) {
            long lastUpdateCheck =
                    preferences.getLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, 0);
            return isTimeForAutoUpdateCheck(lastUpdateCheck, autoUpdateFreq);
        }
        return false;
    }

    public boolean isTimeForAutoUpdateCheck(long lastUpdateCheck, String autoUpdateFreq) {
        int checkEveryNDays;
        if (CommCarePreferences.FREQUENCY_DAILY.equals(autoUpdateFreq)) {
            checkEveryNDays = 1;
        } else {
            checkEveryNDays = 7;
        }
        long duration = DateUtils.DAY_IN_MILLIS * checkEveryNDays;

        return isPending(lastUpdateCheck, duration);
    }

    private boolean isPending(long last, long period) {
        long now = new Date().getTime();

        //1) Straightforward - Time is greater than last + duration
        long diff = now - last;
        if (diff > period) {
            return true;
        }

        //2) For daily stuff, we want it to be the case that if the last time you synced was the day prior,
        //you still sync, so people can get into the cycle of doing it once in the morning, which
        //is more valuable than syncing mid-day.
        if (isDifferentDayInPast(now, last, period)) {
            return true;
        }

        //3) Major time change - (Phone might have had its calendar day manipulated).
        //for now we'll simply say that if last was more than a day in the future (timezone blur)
        //we should also trigger
        return (now < (last - DateUtils.DAY_IN_MILLIS));
    }

    private boolean isDifferentDayInPast(long now, long last, long period) {
        Calendar lastRestoreCalendar = Calendar.getInstance();
        lastRestoreCalendar.setTimeInMillis(last);

        return period == DateUtils.DAY_IN_MILLIS &&
                lastRestoreCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.getInstance().get(Calendar.DAY_OF_WEEK) &&
                now > last;
    }

    /**
     * Whether automated stuff like auto-updates/syncing are valid and should
     * be triggered.
     */
    private boolean areAutomatedActionsInvalid() {
        try {
            return User.TYPE_DEMO.equals(getSession().getLoggedInUser().getUserType());
        } catch (SessionUnavailableException sue) {
            return true;
        }
    }

    private void unbindUserSessionService() {
        synchronized (serviceLock) {
            if (mIsBound) {
                if (sessionWrapper != null) {
                    sessionWrapper.reset();
                }
                mIsBound = false;
                // Detach our existing connection.
                unbindService(mConnection);
                stopService(new Intent(this, CommCareSessionService.class));
            }
        }
    }

    public CommCareSessionService getSession() throws SessionUnavailableException {
        long started = System.currentTimeMillis();
        //If binding is currently in process, just wait for it.
        while (mIsBinding) {
            if (System.currentTimeMillis() - started > mCurrentServiceBindTimeout) {
                //Something bad happened
                unbindUserSessionService();
                throw new SessionUnavailableException("Timeout binding to session service");
            }
        }

        if (mIsBound) {
            synchronized (serviceLock) {
                return mBoundService;
            }
        } else {
            throw new SessionUnavailableException();
        }
    }

    // Start - Error message Hooks

    private final int MESSAGE_NOTIFICATION = org.commcare.dalvik.R.string.notification_message_title;

    private final ArrayList<NotificationMessage> pendingMessages = new ArrayList<>();

    public void reportNotificationMessage(NotificationMessage message) {
        reportNotificationMessage(message, false);
    }

    public void reportNotificationMessage(final NotificationMessage message, boolean notifyUser) {
        synchronized (pendingMessages) {
            //make sure there is no matching message pending
            for (NotificationMessage msg : pendingMessages) {
                if (msg.equals(message)) {
                    //If so, bail.
                    return;
                }
            }
            if (notifyUser) {
                Bundle b = new Bundle();
                b.putParcelable("message", message);
                Message m = Message.obtain(toaster);
                m.setData(b);
                toaster.sendMessage(m);
            }

            //Otherwise, add it to the queue, and update the notification
            pendingMessages.add(message);
            updateMessageNotification();
        }
    }

    private void updateMessageNotification() {
        NotificationManager mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        synchronized (pendingMessages) {
            if (pendingMessages.size() == 0) {
                mNM.cancel(MESSAGE_NOTIFICATION);
                return;
            }

            String title = pendingMessages.get(0).getTitle();

            Notification messageNotification = new Notification(org.commcare.dalvik.R.drawable.notification, title, System.currentTimeMillis());
            messageNotification.number = pendingMessages.size();

            // The PendingIntent to launch our activity if the user selects this notification
            Intent i = new Intent(this, MessageActivity.class);

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i, 0);

            String additional = pendingMessages.size() > 1 ? Localization.get("notifications.prompt.more", new String[]{String.valueOf(pendingMessages.size() - 1)}) : "";
            
            messageNotification = new NotificationCompat.Builder(this)
                    .setContentTitle(title)
                    .setContentText(Localization.get("notifications.prompt.details", new String[]{additional}))
                    .setSmallIcon(org.commcare.dalvik.R.drawable.notification)
                    .setNumber(pendingMessages.size())
                    .setContentIntent(contentIntent)
                    .setDeleteIntent(PendingIntent.getBroadcast(this, 0, new Intent(this, NotificationClearReceiver.class), 0))
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis())
                    .build();

            //Send the notification.
            mNM.notify(MESSAGE_NOTIFICATION, messageNotification);
        }

    }

    public ArrayList<NotificationMessage> purgeNotifications() {
        synchronized (pendingMessages) {
            this.sendBroadcast(new Intent(ACTION_PURGE_NOTIFICATIONS));
            ArrayList<NotificationMessage> cloned = (ArrayList<NotificationMessage>)pendingMessages.clone();
            clearNotifications(null);
            return cloned;
        }
    }

    public void clearNotifications(String category) {
        synchronized (pendingMessages) {
            NotificationManager mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            Vector<NotificationMessage> toRemove = new Vector<>();
            for (NotificationMessage message : pendingMessages) {
                if (category == null || category.equals(message.getCategory())) {
                    toRemove.add(message);
                }
            }

            for (NotificationMessage message : toRemove) {
                pendingMessages.remove(message);
            }
            if (pendingMessages.size() == 0) {
                mNM.cancel(MESSAGE_NOTIFICATION);
            } else {
                updateMessageNotification();
            }
        }
    }

    private boolean syncPending = false;

    /**
     * @return True if there is a sync action pending.
     */
    private boolean getPendingSyncStatus() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();

        long period = -1;

        //Old flag, use a day by default
        if ("true".equals(prefs.getString("cc-auto-update", "false"))) {
            period = DateUtils.DAY_IN_MILLIS;
        }

        //new flag, read what it is.
        String periodic = prefs.getString(CommCarePreferences.AUTO_SYNC_FREQUENCY, CommCarePreferences.FREQUENCY_NEVER);

        if (!periodic.equals(CommCarePreferences.FREQUENCY_NEVER)) {
            period = DateUtils.DAY_IN_MILLIS * (periodic.equals(CommCarePreferences.FREQUENCY_DAILY) ? 1 : 7);
        }

        //If we didn't find a period, bail
        if (period == -1) {
            return false;
        }


        long lastRestore = prefs.getLong(CommCarePreferences.LAST_SYNC_ATTEMPT, 0);

        return (isPending(lastRestore, period));
    }

    public synchronized boolean isSyncPending(boolean clearFlag) {
        if (areAutomatedActionsInvalid()) {
            return false;
        }
        //We only set this to true occasionally, but in theory it could be set to false
        //from other factors, so turn it off if it is.
        if (!getPendingSyncStatus()) {
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

    /**
     * @return a path to a file location that can be used to store a file
     * temporarily and will be cleaned up as part of CommCare's application
     * lifecycle
     */
    public String getTempFilePath() {
        return getAndroidFsTemp() + PropertyUtils.genUUID();
    }

    public ArchiveFileRoot getArchiveFileRoot() {
        return mArchiveFileRoot;
    }

    /**
     * Message handler that pops-up notifications to the user via toast.
     */
    private static class PopupHandler extends Handler {
        /**
         * Reference to the context used to show pop-ups (the parent class).
         * Reference is weak to avoid memory leaks.
         */
        private final WeakReference<CommCareApplication> mActivity;

        /**
         * @param activity Is the context used to pop-up the toast message.
         */
        public PopupHandler(CommCareApplication activity) {
            mActivity = new WeakReference<>(activity);
        }

        /**
         * Pops up the message to the user by way of toast
         *
         * @param m Has a 'message' parcel storing pop-up message text
         */
        @Override
        public void handleMessage(Message m) {
            NotificationMessage message = m.getData().getParcelable("message");

            CommCareApplication activity = mActivity.get();

            if (activity != null && message != null) {
                Toast.makeText(activity,
                        Localization.get("notification.for.details.wrapper",
                                new String[]{message.getTitle()}),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Used for manually linking to a session service during tests
     */
    public void setTestingService(CommCareSessionService service) {
        mIsBound = true;
        mBoundService = service;
        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
            }

            public void onServiceDisconnected(ComponentName className) {
            }
        };
    }
}
