package org.commcare.dalvik.application;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import javax.crypto.SecretKey;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;

import org.commcare.android.database.DbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.DatabaseGlobalOpenHelper;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.CommCareUserOpenHelper;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.User;
import org.commcare.android.db.legacy.LegacyInstallUtils;
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
import org.commcare.android.storage.framework.Table;
import org.commcare.android.tasks.DataSubmissionListener;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.tasks.LogSubmissionTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidUtil;
import org.commcare.android.util.CallInPhoneListener;
import org.commcare.android.util.CommCareExceptionHandler;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.ODKPropertyManager;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.activities.MessageActivity;
import org.commcare.dalvik.activities.UnrecoverableErrorActivity;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.services.CommCareSessionService;
import org.commcare.suite.model.Profile;
import org.commcare.util.CommCareSession;
import org.commcare.util.externalizable.AndroidClassHasher;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.PropertyUtils;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.logic.ArchivedFormManagement;
import org.odk.collect.android.utilities.StethoInitializer;

import android.annotation.SuppressLint;
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
import android.provider.Settings.Secure;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

/**
 * @author ctsims
 */
public class CommCareApplication extends Application {


    public static final int STATE_UNINSTALLED = 0;
    public static final int STATE_UPGRADE = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_CORRUPTED = 4;

    public static final String ACTION_PURGE_NOTIFICATIONS = "CommCareApplication_purge";

    private int dbState;
    private int resourceState;

    private static CommCareApplication app;

    private CommCareApp currentApp;
    
    // stores current state of application: the session, form
    private AndroidSessionWrapper sessionWrapper;

    // Generalize
    private final Object globalDbHandleLock = new Object();
    private SQLiteDatabase globalDatabase;

    //Kind of an odd way to do this
    private boolean updatePending = false;

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

    /**
     * Handler to receive notifications and show them the user using toast.
     */
    private final PopupHandler toaster = new PopupHandler(this);

    /*
     * (non-Javadoc)
     * @see android.app.Application#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        StethoInitializer.initStetho(this);
        Collect.setStaticApplicationContext(this);
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

        Thread.setDefaultUncaughtExceptionHandler(new CommCareExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()));

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
        } catch (StorageFullException sfe) {
            throw new RuntimeException(sfe);
        } finally {
            //No matter what happens, set up our new logger, we want those logs!
            Logger.registerLogger(new AndroidLogger(this.getGlobalStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class)));
            pil.dumpToNewLogger();
        }

        intializeDefaultLocalizerData();

        //The fallback in case the db isn't installed 
        resourceState = initializeAppResources();
    }

    public void triggerHandledAppExit(Context c, String message) {
        triggerHandledAppExit(c, message, Localization.get("app.handled.error.title"));
    }

    public void triggerHandledAppExit(Context c, String message, String title) {
        Intent i = new Intent(c, UnrecoverableErrorActivity.class);
        i.putExtra(UnrecoverableErrorActivity.EXTRA_ERROR_TITLE, title);
        i.putExtra(UnrecoverableErrorActivity.EXTRA_ERROR_MESSAGE, message);

        //start a new stack and forget where we were (so we don't restart the app from there)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        c.startActivity(i);
    }

    /**
     * Close down the commcare session and login session.
     */
    public void logout() {
        synchronized(serviceLock) {
            if(this.sessionWrapper != null) {
                // clear out commcare session
                sessionWrapper.reset();
            }
            
            // close down the commcare login session
            doUnbindService();
        }
    }
    
    /**
     * Start commcare login session.
     */
    public void logIn(byte[] symetricKey, UserKeyRecord record) {
        synchronized(serviceLock) {
            // if we already have a connection established to
            // CommCareSessionService, close it and open a new one
            if(this.mIsBound) {
                logout();
            }
            doBindService(symetricKey, record);
        }
    }

    public SecretKey createNewSymetricKey() {
        return getSession().createNewSymetricKey();
    }

    private CallInPhoneListener listener = null;

    private void attachCallListener() {
        TelephonyManager tManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);

        listener = new CallInPhoneListener(this, this.getCommCarePlatform());
        listener.startCache();

        tManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    public CallInPhoneListener getCallListener() {
        return listener;
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
     *
     * @return
     * @throws SessionUnavailableException If there is no session current being
     *                                     executed
     */
    public CommCareSession getCurrentSession() {
        return getCurrentSessionWrapper().getSession();
    }

    public AndroidSessionWrapper getCurrentSessionWrapper() {
        if (sessionWrapper == null) {
            throw new SessionUnavailableException();
        }
        return sessionWrapper;
    }

    public int getDatabaseState() {
        return dbState;
    }

    public int getAppResourceState() {
        return resourceState;
    }

    public void initializeGlobalResources(CommCareApp app) {
        if (dbState != STATE_UNINSTALLED) {
            resourceState = initializeAppResources(app);
        }
    }

    public String getPhoneId() {
        TelephonyManager manager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
        String imei = manager.getDeviceId();
        if (imei == null) {
            imei = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        }
        return imei;
    }

    public void intializeDefaultLocalizerData() {
        Localization.init(true);
        Localization.registerLanguageReference("default", "jr://asset/locales/messages_ccodk_default.txt");
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
        ReferenceManager._().addRootTranslator(new RootTranslator("jr://media/", GlobalConstants.MEDIA_REF));
    }

    private int initializeAppResources() {
        //There should be exactly one of these for now
        for (ApplicationRecord record : getGlobalStorage(ApplicationRecord.class)) {
            if (record.getStatus() == ApplicationRecord.STATUS_INSTALLED) {
                //We have an app record ready to go
                return initializeAppResources(new CommCareApp(record));
            }
        }
        return STATE_UNINSTALLED;
    }

    private int initializeAppResources(CommCareApp app) {
        try {
            currentApp = app;

            if (currentApp.initializeApplication()) {
                return STATE_READY;
            } else {
                //????
                return STATE_CORRUPTED;
            }
        } catch (Exception e) {
            Log.i("FAILURE", "Problem with loading");
            Log.i("FAILURE", "E: " + e.getMessage());
            e.printStackTrace();
            ExceptionReportTask ert = new ExceptionReportTask();
            ert.execute(e);
            return STATE_CORRUPTED;
        }
    }

    private int initGlobalDb() {
        SQLiteDatabase database;
        try {
            database = new DatabaseGlobalOpenHelper(this).getWritableDatabase("null");
            database.close();
            return STATE_READY;
        } catch (SQLiteException e) {
            //Only thrown in DB isn't there
            return STATE_UNINSTALLED;
        }
    }

    public SQLiteDatabase getUserDbHandle() {
        return this.getSession().getUserDbHandle();
    }

    public <T extends Persistable> SqlStorage<T> getGlobalStorage(Class<T> c) {
        return getGlobalStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getGlobalStorage(String table, Class<T> c) {
        return new SqlStorage<T>(table, c, new DbHelper(this.getApplicationContext()) {
            /*
             * (non-Javadoc)
             * @see org.commcare.android.database.DbHelper#getHandle()
             */
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

    public <T extends Persistable> SqlStorage<T> getAppStorage(Class<T> c) throws SessionUnavailableException {
        return getAppStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getAppStorage(String name, Class<T> c) throws SessionUnavailableException {
        return currentApp.getStorage(name, c);
    }

    public <T extends Persistable> SqlStorage<T> getUserStorage(Class<T> c) throws SessionUnavailableException {
        return getUserStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getUserStorage(String storage, Class<T> c) throws SessionUnavailableException {
        return new SqlStorage<T>(storage, c, new DbHelper(this.getApplicationContext()) {
            /*
             * (non-Javadoc)
             * @see org.commcare.android.database.DbHelper#getHandle()
             */
            @Override
            public SQLiteDatabase getHandle() {
                SQLiteDatabase database = getUserDbHandle();
                if (database == null) {
                    throw new NullPointerException("Somehow didn't get a database handle!");
                }
                return database;
            }
        });
    }

    public <T extends Persistable> SqlStorage<T> getRawStorage(String storage, Class<T> c, final SQLiteDatabase handle) {
        return new SqlStorage<T>(storage, c, new DbHelper(this.getApplicationContext()) {
            /*
             * (non-Javadoc)
             * @see org.commcare.android.database.DbHelper#getHandle()
             */
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
    public void clearUserData() throws SessionUnavailableException {
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

        // TODO PLM wrap in try/catch for SessionUnavailableException
        final String username = this.getSession().getLoggedInUser().getUsername();

        final Set<String> dbIdsToRemove = new HashSet<String>();

        this.getAppStorage(UserKeyRecord.class).removeAll(new EntityFilter<UserKeyRecord>() {

            /*
             * (non-Javadoc)
             * @see org.javarosa.core.services.storage.EntityFilter#matches(java.lang.Object)
             */
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
        //Should be good to go. The app'll log us out now that there's no user details in memory
        logout();

        Editor sharedPreferencesEditor = CommCareApplication._().getCurrentApp().getAppPreferences().edit();

        sharedPreferencesEditor.putString(CommCarePreferences.LAST_LOGGED_IN_USER, null);

        sharedPreferencesEditor.commit();

        for (String id : dbIdsToRemove) {
            //TODO: We only wanna do this if the user is the _last_ one with a key to this id, actually.
            //(Eventually)
            this.getDatabasePath(CommCareUserOpenHelper.getDbName(id)).delete();
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
     *
     * @param timeout
     */
    public void setCustomServiceBindTimeout(int timeout) {
        synchronized (serviceLock) {
            this.mCurrentServiceBindTimeout = timeout;
        }
    }
    
    void doBindService(final byte[] key, final UserKeyRecord record) {
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

                    mBoundService = ((CommCareSessionService.LocalBinder) service).getService();

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
                        getSession().startSession(user);
                        attachCallListener();
                        CommCareApplication.this.sessionWrapper = new AndroidSessionWrapper(CommCareApplication.this.getCommCarePlatform());

                        //See if there's an auto-update pending. We only want to be able to turn this
                        //to "True" on login, not any other time
                        //TODO: this should be associated with the app itself, not the global settings
                        updatePending = getPendingUpdateStatus();
                        syncPending = getPendingSyncStatus();

                        doReportMaintenance(false);

                        //Register that this user was the last to successfully log in if it's a real user
                        if (!User.TYPE_DEMO.equals(user.getUserType())) {
                            getCurrentApp().getAppPreferences().edit().putString(CommCarePreferences.LAST_LOGGED_IN_USER, record.getUsername()).commit();
                            ArchivedFormManagement.performArchivedFormPurge(getCurrentApp());
                        }
                    }
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
        bindService(new Intent(this, CommCareSessionService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBinding = true;
    }

    @SuppressLint("NewApi")
    protected void doReportMaintenance(boolean force) {
        //OK. So for now we're going to daily report sends and not bother with any of the frequency properties.


        //Create a new submission task no matter what. If nothing is pending, it'll see if there are unsent reports
        //and try to send them. Otherwise, it'll create the report
        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        String url = settings.getString("PostURL", null);

        if (url == null) {
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "PostURL isn't set. This should never happen");
            return;
        }

        DataSubmissionListener dataListener = null;

        try {
            dataListener =
                CommCareApplication.this.getSession().startDataSubmissionListener(R.string.submission_logs_title);
        } catch (SessionUnavailableException sue) {
            // abort since it looks like the session expired
            return;
        }

        LogSubmissionTask task = new LogSubmissionTask(this,
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

    private boolean getPendingUpdateStatus() {
        SharedPreferences preferences = getCurrentApp().getAppPreferences();
        //Establish whether or not an AutoUpdate is Pending
        String autoUpdateFreq = preferences.getString(CommCarePreferences.AUTO_UPDATE_FREQUENCY, CommCarePreferences.FREQUENCY_NEVER);

        //See if auto update is even turned on
        if (!autoUpdateFreq.equals(CommCarePreferences.FREQUENCY_NEVER)) {
            long lastUpdateCheck = preferences.getLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, 0);

            long duration = (24 * 60 * 60 * 100) * (CommCarePreferences.FREQUENCY_DAILY.equals(autoUpdateFreq) ? 1 : 7);

            return isPending(lastUpdateCheck, duration);
        }
        return false;
    }

    private boolean isPending(long last, long period) {
        Date current = new Date();
        //There are a couple of conditions in which we want to trigger pending maintenance ops.

        long now = current.getTime();

        //1) Straightforward - Time is greater than last + duration
        long diff = now - last;
        if (diff > period) {
            return true;
        }

        Calendar lastRestoreCalendar = Calendar.getInstance();
        lastRestoreCalendar.setTimeInMillis(last);

        //2) For daily stuff, we want it to be the case that if the last time you synced was the day prior, 
        //you still sync, so people can get into the cycle of doing it once in the morning, which
        //is more valuable than syncing mid-day.        
        if (period == DateUtils.DAY_IN_MILLIS &&
                (lastRestoreCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.getInstance().get(Calendar.DAY_OF_WEEK))) {
            return true;
        }

        //3) Major time change - (Phone might have had its calendar day manipulated).
        //for now we'll simply say that if last was more than a day in the future (timezone blur)
        //we should also trigger
        if (now < (last - DateUtils.DAY_IN_MILLIS)) {
            return true;
        }

        //TODO: maaaaybe trigger all if there's a substantial time difference
        //noted between calls to a server

        //Otherwise we're fine
        return false;
    }

    /**
     * Whether automated stuff like auto-updates/syncing are valid and should
     * be triggered.
     *
     * @return
     */
    private boolean areAutomatedActionsInvalid() {
        try {
            if (User.TYPE_DEMO.equals(getSession().getLoggedInUser().getUserType())) {
                return true;
            }
        } catch (SessionUnavailableException sue) {
            return true;
        }
        return false;
    }

    public boolean isUpdatePending() {
        if (areAutomatedActionsInvalid()) {
            return false;
        }
        // We only set this to true occasionally, but in theory it could be set
        // to false from other factors, so turn it off if it is.
        if (!getPendingUpdateStatus()) {
            updatePending = false;
        }
        return updatePending;
    }

    /**
     * Logout of commcare login session and close down connection to the bound
     * service.
     */
    void doUnbindService() {
        synchronized (serviceLock) {
            if (mIsBound) {
                mIsBound = false;
                // Detach our existing connection.
                unbindService(mConnection);
            }
        }
    }

    //Milliseconds to wait for bind
    private static final int MAX_BIND_TIMEOUT = 5000;

    private int mCurrentServiceBindTimeout = MAX_BIND_TIMEOUT;

    public CommCareSessionService getSession() throws SessionUnavailableException {
        long started = System.currentTimeMillis();
        //If binding is currently in process, just wait for it.
        while (mIsBinding) {
            if (System.currentTimeMillis() - started > mCurrentServiceBindTimeout) {
                //Something bad happened
                doUnbindService();
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


    public Pair<Long, int[]> getSyncDisplayParameters() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        long lastSync = prefs.getLong("last-succesful-sync", 0);
        int unsentForms = this.getUserStorage(FormRecord.class).getIDsForValue(FormRecord.META_STATUS, FormRecord.STATUS_UNSENT).size();
        int incompleteForms = this.getUserStorage(FormRecord.class).getIDsForValue(FormRecord.META_STATUS, FormRecord.STATUS_INCOMPLETE).size();

        return new Pair<Long, int[]>(lastSync, new int[]{unsentForms, incompleteForms});
    }


    // Start - Error message Hooks

    private final int MESSAGE_NOTIFICATION = org.commcare.dalvik.R.string.notification_message_title;

    private final ArrayList<NotificationMessage> pendingMessages = new ArrayList<NotificationMessage>();

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

    public void updateMessageNotification() {
        NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
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

            // Set the info for the views that show in the notification panel.
            messageNotification.setLatestEventInfo(this, title, Localization.get("notifications.prompt.details", new String[]{additional}), contentIntent);

            messageNotification.deleteIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, NotificationClearReceiver.class), 0);

            //Send the notification.
            mNM.notify(MESSAGE_NOTIFICATION, messageNotification);
        }

    }

    public ArrayList<NotificationMessage> purgeNotifications() {
        synchronized (pendingMessages) {
            this.sendBroadcast(new Intent(ACTION_PURGE_NOTIFICATIONS));
            ArrayList<NotificationMessage> cloned = (ArrayList<NotificationMessage>) pendingMessages.clone();
            clearNotifications(null);
            return cloned;
        }
    }

    public void clearNotifications(String category) {
        synchronized (pendingMessages) {
            NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Vector<NotificationMessage> toRemove = new Vector<NotificationMessage>();
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

    // End - Error Message Hooks

    private boolean syncPending = false;

    /**
     * @return True if there is a sync action pending. False otherwise.
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
     * Notify the application that something has occurred which has been logged, and which should
     * cause log submission to occur as soon as possible.
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
            mActivity = new WeakReference<CommCareApplication>(activity);
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

            if (activity != null) {
                Toast.makeText(activity,
                        Localization.get("notification.for.details.wrapper",
                            new String[]{message.getTitle()}),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

}
