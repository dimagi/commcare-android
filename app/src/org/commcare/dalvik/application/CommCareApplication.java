/**
 * 
 */
package org.commcare.dalvik.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
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
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.SqlStorageIterator;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.DatabaseGlobalOpenHelper;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.User;
import org.commcare.android.db.legacy.LegacyInstallUtils;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.javarosa.PreInitLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.notifications.NotificationClearReceiver;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.references.AssetFileRoot;
import org.commcare.android.references.JavaHttpRoot;
import org.commcare.android.storage.framework.Table;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.LogSubmissionTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.CallInPhoneListener;
import org.commcare.android.util.CommCareExceptionHandler;
import org.commcare.android.util.ODKPropertyManager;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.MessageActivity;
import org.commcare.dalvik.activities.UnrecoverableErrorActivity;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.services.CommCareSessionService;
import org.commcare.suite.model.Profile;
import org.commcare.util.CommCareSession;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.Externalizable;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

/**
 * @author ctsims
 *
 */
public class CommCareApplication extends Application {

	
	public static final int STATE_UNINSTALLED = 0;
	public static final int STATE_UPGRADE = 1;
	public static final int STATE_READY = 2;
	public static final int STATE_CORRUPTED = 4;
	
	private int dbState;
	private int resourceState;
	
	private static CommCareApplication app;
	
	private CommCareApp currentApp;
	
	private AndroidSessionWrapper sessionWrapper;
	
	/** Generalize **/
	private Object globalDbHandleLock = new Object();
	private SQLiteDatabase globalDatabase;
	
	//Kind of an odd way to do this
	boolean updatePending = false;

	@Override
	public void onCreate() {
		super.onCreate();
		
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
		
		//Init global storage (Just application records, etc)
		dbState = initGlobalDb();
		
		try {
			LegacyInstallUtils.checkForLegacyInstall(this, this.getGlobalStorage(ApplicationRecord.class));
		} catch(StorageFullException sfe) {
			throw new RuntimeException(sfe);
		}
		
//        PreferenceChangeListener listener = new PreferenceChangeListener(this);
//        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(listener);
        
        defaultLocalizations();
        
		//The fallback in case the db isn't installed 
		resourceState = STATE_UNINSTALLED;
		
		//We likely want to do this for all of the storage, this is just a way to deal with fixtures
		//temporarily. 
		//StorageManager.registerStorage("fixture", this.getStorage("fixture", FormInstance.class));
		
//		Logger.registerLogger(new AndroidLogger(CommCareApplication._().getStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class)));
//		
//		//Dump any logs we've been keeping track of in memory to storage
//		pil.dumpToNewLogger();
		
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

	public void logout() {
		synchronized(serviceLock) {

			if(this.sessionWrapper != null) {
				sessionWrapper.reset();
			}
	        
	        doUnbindService();
		}
	}
	
	public void logIn(byte[] symetricKey, UserKeyRecord record) {
		synchronized(serviceLock) {
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
	
	private void detachCallListener() {
		if(listener != null) {
			TelephonyManager tManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
			tManager.listen(listener, PhoneStateListener.LISTEN_NONE);	
			listener = null;
		}
	}
	
	public CallInPhoneListener getCallListener() {
		return listener;
	}
	
	
	public int versionCode() {
		try {
			PackageManager pm = this.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
			return pi.versionCode;
		} catch(NameNotFoundException e) {
			throw new RuntimeException("Android package name not available.");
		}
	}
    
	public int[] getCommCareVersion() {
		return this.getResources().getIntArray(R.array.commcare_version);
	}

	public AndroidCommCarePlatform getCommCarePlatform() {
		if(this.currentApp == null) {
			throw new RuntimeException("No App installed!!!");
		} else {
			return this.currentApp.getCommCarePlatform();
		}
	}
	
	public CommCareApp getCurrentApp() {
		return this.currentApp;
	}
	
	public CommCareSession getCurrentSession() {
		return getCurrentSessionWrapper().getSession();
	}

	public AndroidSessionWrapper getCurrentSessionWrapper() {
		if(sessionWrapper == null) { throw new SessionUnavailableException(); }
		return sessionWrapper;
	}
	
	public int getDatabaseState() {
		return dbState;
	}
	public int getAppResourceState() {
		return resourceState;
	}
	
	public void initializeGlobalResources(CommCareApp app) {
		if(dbState != STATE_UNINSTALLED) {
			resourceState = initializeAppResources(app);
		}
	}
	
	public String getPhoneId() {
		TelephonyManager manager = (TelephonyManager)this.getSystemService(TELEPHONY_SERVICE);
		String imei = manager.getDeviceId();
		if(imei == null) {
			imei = android.provider.Settings.Secure.ANDROID_ID;
		}
		return imei;
	}
	
	private void defaultLocalizations() {
		Localization.registerLanguageReference("default", "jr://asset/locales/messages_ccodk_default.txt");
		Localization.setDefaultLocale("default");
		
		//For now. Possibly handle this better in the future
		Localization.setLocale("default");
	}
	
	private void setRoots() {
		JavaHttpRoot http = new JavaHttpRoot();
		
		AssetFileRoot afr = new AssetFileRoot(this);

		ReferenceManager._().addReferenceFactory(http);
		ReferenceManager._().addReferenceFactory(afr);
		//ReferenceManager._().addRootTranslator(new RootTranslator("jr://resource/",GlobalConstants.RESOURCE_PATH));
		ReferenceManager._().addRootTranslator(new RootTranslator("jr://media/",GlobalConstants.MEDIA_REF));
	}
	
	private int initializeAppResources() {
		//There should be exactly one of these for now
		for(ApplicationRecord record : getGlobalStorage(ApplicationRecord.class)) {
			if(record.getStatus() == ApplicationRecord.STATUS_INSTALLED) {
				//We have an app record ready to go
				return initializeAppResources(new CommCareApp(record));
			}
		}
		return STATE_UNINSTALLED;
	}

	private int initializeAppResources(CommCareApp app) {
		try {
			currentApp = app;
			if(currentApp.initializeApplication()) {
				return STATE_READY;
			} else {
				//????
				return STATE_CORRUPTED;
			}
		}
		catch(Exception e) {
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
			database = new DatabaseGlobalOpenHelper(this).getWritableDatabase(null);
			database.close();
			return STATE_READY;
		} catch(SQLiteException e) {
			//Only thrown in DB isn't there
			return STATE_UNINSTALLED;
		}
	}
		
	public SQLiteDatabase getUserDbHandle() {
		return this.getSession().getUserDbHandle();
	}
	
	public <T extends Persistable> SqlIndexedStorageUtility<T> getGlobalStorage(Class<T> c) {
		return new SqlIndexedStorageUtility<T>(c.getAnnotation(Table.class).value(), c, new DbHelper(this.getApplicationContext()){
			@Override
			public SQLiteDatabase getHandle() {
				synchronized(globalDbHandleLock) {
					if(globalDatabase == null || !globalDatabase.isOpen()) {
						globalDatabase = new DatabaseGlobalOpenHelper(this.c).getWritableDatabase(null);
					}
					return globalDatabase;
				}
			}
		});
	}
	
	public <T extends Persistable> SqlIndexedStorageUtility<T> getAppStorage(Class<T> c) throws SessionUnavailableException {
		return getAppStorage(c.getAnnotation(Table.class).value(), c);
	}
	
	public <T extends Persistable> SqlIndexedStorageUtility<T> getAppStorage(String name, Class<T> c) throws SessionUnavailableException {
		return currentApp.getStorage(name, c);
	}
	
	public <T extends Persistable> SqlIndexedStorageUtility<T> getUserStorage(Class<T> c) throws SessionUnavailableException {
		return getUserStorage(c.getAnnotation(Table.class).value(), c);
	}
	
	public <T extends Persistable> SqlIndexedStorageUtility<T> getUserStorage(String storage, Class<T> c) throws SessionUnavailableException {
		return new SqlIndexedStorageUtility<T>(storage, c, new DbHelper(this.getApplicationContext()){
			@Override
			public SQLiteDatabase getHandle() {
				SQLiteDatabase database = getUserDbHandle();
				if(database == null) {
					throw new NullPointerException("Somehow didn't get a database handle!");
				}
				return database;
			}
		});
	}
	
	public <T extends Persistable> SqlIndexedStorageUtility<T> getRawStorage(String storage, Class<T> c, final SQLiteDatabase handle) {
		return new SqlIndexedStorageUtility<T>(storage, c, new DbHelper(this.getApplicationContext()){
			@Override
			public SQLiteDatabase getHandle() {
				return handle;
			}
		});
	}
	
		
	public void serializeToIntent(Intent i, String name, Externalizable data) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			data.writeExternal(new DataOutputStream(baos));
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		i.putExtra(name, baos.toByteArray());
	}
	
	public <T extends Externalizable> T deserializeFromIntent(Intent i, String name, Class<T> type) {
		if(!i.hasExtra(name)) { return null;}
		T t;
		try {
			t = type.newInstance();
			t.readExternal(new DataInputStream(new ByteArrayInputStream(i.getByteArrayExtra(name))), DbUtil.getPrototypeFactory(this));
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (DeserializationException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
			throw new RuntimeException(e1);
		} catch (InstantiationException e1) {
			e1.printStackTrace();
			throw new RuntimeException(e1);
		}
		return t;
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
	}

	public static CommCareApplication _() {
		return app;
	}
	
	/**
	 * This method goes through and identifies whether there are elements in the
	 * database which point to/expect files to exist on the file system, and clears
	 * out any records which refer to files that don't exist. 
	 */
	public void cleanUpDatabaseFileLinkages() throws SessionUnavailableException{
		Vector<Integer> toDelete = new Vector<Integer>();
		
		SqlIndexedStorageUtility<FormRecord> storage = getUserStorage(FormRecord.class);
		
		//Can't load the records outright, since we'd need to be logged in (The key is encrypted)
		for(SqlStorageIterator iterator = storage.iterate(); iterator.hasMore();) {
			int id = iterator.nextID();
			String instanceRecordUri = storage.getMetaDataFieldForRecord(id, FormRecord.META_INSTANCE_URI);
			if(instanceRecordUri == null) {
				toDelete.add(id);
				continue;
			}
			
			//otherwise, grab this record and see if the file's around
			
			Cursor c = this.getContentResolver().query(Uri.parse(instanceRecordUri), new String[] { InstanceColumns.INSTANCE_FILE_PATH}, null, null, null);
			if(!c.moveToFirst()) { toDelete.add(id);}
			else {
				String path = c.getString(c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
				if(path == null || !new File(path).exists()) {
					toDelete.add(id);
				}
			}
			c.close();
		}
		
		for(int recordid : toDelete) {
			//this should go to the form record wipe cleanup task
			storage.remove(recordid);
		}
	}

	/**
	 * This method wipes out all local user data (users, referrals, etc) but leaves
	 * application resources in place.
	 * 
	 * It makes no attempt to make sure this is a safe operation when called, so
	 * it shouldn't be used lightly.
	 */
	public void clearUserData() throws SessionUnavailableException {
//		//First clear anything that will require the user's key, since we're going to wipe it out!
//		getStorage(ACase.STORAGE_KEY, ACase.class).removeAll();
//		
//		//TODO: We should really be wiping out the _stored_ instances here, too
//		getStorage(FormRecord.STORAGE_KEY, FormRecord.class).removeAll();
//		
//		//Also, any of the sessions we've got saved
//		getStorage(SessionStateDescriptor.STORAGE_KEY, SessionStateDescriptor.class).removeAll();
//		
//		//Now we wipe out the user entirely
//		getStorage(User.STORAGE_KEY, User.class).removeAll();
//		
//		//Get rid of any user fixtures
//		getStorage("fixture", FormInstance.class).removeAll();
//		
//		getStorage(GeocodeCacheModel.STORAGE_KEY, GeocodeCacheModel.class).removeAll();
		
		final String username = this.getSession().getLoggedInUser().getUsername();
		
		final Set<String> dbIdsToRemove = new HashSet<String>();
		
		this.getAppStorage(UserKeyRecord.class).removeAll(new EntityFilter<UserKeyRecord>() {

			@Override
			public boolean matches(UserKeyRecord ukr) {
				dbIdsToRemove.add(ukr.getUuid());
				return ukr.getUsername().toLowerCase().equals(username.toLowerCase());
			}
			
		});
		
		//TODO: We can just delete the db entirely. 
		//Should be good to go. The app'll log us out now that there's no user details in memory
		logout();
		
		for(String id : dbIdsToRemove) {
			this.getDatabasePath("database_user" + id).delete();
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
		for(int vn: versions) {
			if(ccv != "") {
				ccv +=".";
			}
			ccv += vn;
		}
		
		
		String profileVersion = "";
		
		Profile p = this.currentApp == null ? null : this.getCommCarePlatform().getCurrentProfile();
		if(p != null) {
			profileVersion = String.valueOf(p.getVersion());
		}

		
		String buildDate = getString(R.string.app_build_date);
		String buildNumber = getString(R.string.app_build_number);
		
		return Localization.get(getString(R.string.app_version_string), new String[] {pi.versionName, String.valueOf(pi.versionCode), ccv, buildNumber, buildDate, profileVersion});
	}
	
	//Start Service code. Will be changed in the future
	private CommCareSessionService mBoundService;

	private ServiceConnection mConnection;

	private Object serviceLock = new Object();
	boolean mIsBound = false;
	boolean mIsBinding = false;
	void doBindService(final byte[] key, final UserKeyRecord record) {
		mConnection = new ServiceConnection() {
		    public void onServiceConnected(ComponentName className, IBinder service) {
		        // This is called when the connection with the service has been
		        // established, giving us the service object we can use to
		        // interact with the service.  Because we have bound to a explicit
		        // service that we know is running in our own process, we can
		        // cast its IBinder to a concrete class and directly access it.
		    	User user = null;
				synchronized(serviceLock) {
					mBoundService = ((CommCareSessionService.LocalBinder)service).getService();

					//Don't let anyone touch this until it's logged in
					mBoundService.prepareStorage(key, record);
			    
				    if(record != null) {
						//Ok, so we have a login that was successful, but do we have a user model in the DB?
				    	//We need to check before we're logged in, so we get the handle raw, here
						for(User u : getRawStorage("USER", User.class, mBoundService.getUserDbHandle()) ){
							if(record.getUsername().equals(u.getUsername())) {
								user = u;
							}
						}
				    }
				    
					//service available
				    mIsBound = true;
			        
			        //Don't signal bind completion until the db is initialized.
				    mIsBinding = false;
				
				if(user != null) {
					getSession().logIn(user);
					attachCallListener();
					CommCareApplication.this.sessionWrapper = new AndroidSessionWrapper(CommCareApplication.this.getCommCarePlatform());
					
					//See if there's an auto-update pending. We only want to be able to turn this
					//to "True" on login, not any other time
					//TODO: this should be associated with the app itself, not the global settings
					updatePending = getPendingUpdateStatus();
					syncPending = getPendingSyncStatus();
					
					doReportMaintenance(false);
					
					//Register that this user was the last to succesfully log in
					getCurrentApp().getAppPreferences().edit().putString(CommCarePreferences.LAST_LOGGED_IN_USER, record.getUsername()).commit();
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
	    bindService(new Intent(this,  CommCareSessionService.class), mConnection, Context.BIND_AUTO_CREATE);
	    mIsBinding = true;
	}
	
	protected void doReportMaintenance(boolean force) {
		//OK. So for now we're going to daily report sends and not bother with any of the frequency properties.
		
		
		//Create a new submission task no matter what. If nothing is pending, it'll see if there are unsent reports
		//and try to send them. Otherwise, it'll create the report
		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
		String url = settings.getString("PostURL", null);
		
		if(url == null) {
			Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "PostURL isn't set. This should never happen");
			return;
		}
		
		LogSubmissionTask task = new LogSubmissionTask(this,
				force || isPending(settings.getLong(CommCarePreferences.LOG_LAST_DAILY_SUBMIT, 0), DateUtils.DAY_IN_MILLIS),
				CommCareApplication.this.getSession().startDataSubmissionListener(R.string.submission_logs_title),
				url);
		
		task.execute();
	}

	private boolean getPendingUpdateStatus() {
		SharedPreferences preferences = getCurrentApp().getAppPreferences();
		//Establish whether or not an AutoUpdate is Pending
		String autoUpdateFreq = preferences.getString(CommCarePreferences.AUTO_UPDATE_FREQUENCY, CommCarePreferences.FREQUENCY_NEVER);

		//See if auto update is even turned on
		if(autoUpdateFreq != CommCarePreferences.FREQUENCY_NEVER) {
			long lastUpdateCheck = preferences.getLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, 0);

			long duration = (24*60*60*100) * (autoUpdateFreq == CommCarePreferences.FREQUENCY_DAILY ? 1 : 7);
			
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
		if( diff > period) {
			return true;
		}
		
		Calendar lastRestoreCalendar = Calendar.getInstance();
		lastRestoreCalendar.setTimeInMillis(last);
		
		//2) For daily stuff, we want it to be the case that if the last time you synced was the day prior, 
		//you still sync, so people can get into the cycle of doing it once in the morning, which
		//is more valuable than syncing mid-day.		
		if(period == DateUtils.DAY_IN_MILLIS && 
		   (lastRestoreCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.getInstance().get(Calendar.DAY_OF_WEEK))) {
			return true;
		}
		
		//3) Major time change - (Phone might have had its calendar day manipulated).
		//for now we'll simply say that if last was more than a day in the future (timezone blur)
		//we should also trigger
		if(now < (last - DateUtils.DAY_IN_MILLIS)) {
			return true;
		}
		
		//TODO: maaaaybe trigger all if there's a substantial time difference
		//noted between calls to a server
		
		//Otherwise we're fine
		return false;
	}
	
	public boolean isUpdatePending() {
		//We only set this to true occasionally, but in theory it could be set to false 
		//from other factors, so turn it off if it is.
		if(getPendingUpdateStatus() == false) {
			updatePending = false;
		}
		return updatePending;
	}

	void doUnbindService() {
		synchronized(serviceLock) {
		    if (mIsBound) {
		        mIsBound = false;
		    	mBoundService.logout();
		        // Detach our existing connection.
		        unbindService(mConnection);
		    }
		}
	}
	
	//Milliseconds to wait for bind
	private static final int MAX_BIND_TIMEOUT = 5000;
	
	public CommCareSessionService getSession() throws SessionUnavailableException {
		long started = System.currentTimeMillis();
		//If binding is currently in process, just wait for it.
		while(mIsBinding) {
			if(System.currentTimeMillis() - started > MAX_BIND_TIMEOUT) {
				//Something bad happened
				doUnbindService();
				throw new SessionUnavailableException("Timeout binding to session service");
			}
		}
		
		if(mIsBound) {
			synchronized(serviceLock) {
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
    	
    	return new Pair<Long,int[]>(lastSync, new int[] {unsentForms, incompleteForms});
	}
	

	// Start - Error message Hooks
	
    private int MESSAGE_NOTIFICATION = org.commcare.dalvik.R.string.notification_message_title;
	
    ArrayList<NotificationMessage> pendingMessages = new ArrayList<NotificationMessage>();
    
    Handler toaster = new Handler(){
	    @Override
	    public void handleMessage(Message m) {
	    	NotificationMessage message = m.getData().getParcelable("message");
			Toast.makeText(CommCareApplication.this,
					Localization.get("install.error.details", new String[] {message.getTitle()}),
					Toast.LENGTH_LONG).show();
	    }
	};
    
    public void reportNotificationMessage(NotificationMessage message) {
    	reportNotificationMessage(message, false);
    }
	public void reportNotificationMessage(final NotificationMessage message, boolean notifyUser) {
		synchronized(pendingMessages) {
			//make sure there is no matching message pending
			for(NotificationMessage msg : pendingMessages) {
				if(msg.equals(message)) {
					//If so, bail.
					return;
				}
			}
			if(notifyUser) {
				Bundle b = new Bundle(); b.putParcelable("message", message);
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
		NotificationManager mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		synchronized(pendingMessages) {
			if(pendingMessages.size() == 0) { 
				mNM.cancel(MESSAGE_NOTIFICATION);
				return;
			}
			
			String title = pendingMessages.get(0).getTitle();
			
			Notification messageNotification = new Notification(org.commcare.dalvik.R.drawable.notification, title, System.currentTimeMillis());
			messageNotification.number = pendingMessages.size();
			
	        // The PendingIntent to launch our activity if the user selects this notification
	        Intent i = new Intent(this, MessageActivity.class);
	        
	        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i, 0);
	        
	        String additional = pendingMessages.size() > 1 ? Localization.get("notifications.prompt.more", new String[] {String.valueOf(pendingMessages.size() - 1)}) : ""; 
	        
	
	        // Set the info for the views that show in the notification panel.
	        messageNotification.setLatestEventInfo(this, title, Localization.get("notifications.prompt.details", new String[] {additional}), contentIntent);
	        
	        messageNotification.deleteIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, NotificationClearReceiver.class), 0);
	
	    	//Send the notification.
	    	mNM.notify(MESSAGE_NOTIFICATION, messageNotification);
		}

	}
	
	public ArrayList<NotificationMessage> purgeNotifications() {
		synchronized(pendingMessages) {
			ArrayList<NotificationMessage> cloned = (ArrayList<NotificationMessage>)pendingMessages.clone();
			clearNotifications(null);
			return cloned;
		}
	}
	
	public void clearNotifications(String category) {
		synchronized(pendingMessages) {
			NotificationManager mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
			Vector<NotificationMessage> toRemove = new Vector<NotificationMessage>();
			for(NotificationMessage message : pendingMessages) {
				if(category == null || message.getCategory() == category) {
					toRemove.add(message);
				}
			}
			
			for(NotificationMessage message : toRemove) { pendingMessages.remove(message); }
			if(pendingMessages.size() == 0) { mNM.cancel(MESSAGE_NOTIFICATION); }
			else { updateMessageNotification(); }
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
    	if("true".equals(prefs.getString("cc-auto-update","false"))) { period = DateUtils.DAY_IN_MILLIS;}
    	
    	//new flag, read what it is.
    	String periodic = prefs.getString(CommCarePreferences.AUTO_SYNC_FREQUENCY,CommCarePreferences.FREQUENCY_NEVER);
    	
    	if(!periodic.equals(CommCarePreferences.FREQUENCY_NEVER)) {
    		period = DateUtils.DAY_IN_MILLIS * (periodic.equals(CommCarePreferences.FREQUENCY_DAILY) ? 1 : 7);
    	}
    	
    	//If we didn't find a period, bail
    	if(period == -1 ) { return false; }

		
		long lastRestore = prefs.getLong(CommCarePreferences.LAST_SYNC_ATTEMPT, 0);
		
		if(isPending(lastRestore, period)) {
			return true;
		}
		return false;
    }

	public synchronized boolean isSyncPending(boolean clearFlag) {
		//We only set this to true occasionally, but in theory it could be set to false 
		//from other factors, so turn it off if it is.
		if(getPendingSyncStatus() == false) {
			syncPending = false;
		}
		if(!syncPending) { return false; }
		if(clearFlag) { syncPending = false; }
		return true;
	}

	public boolean isStorageAvailable() {
		try {
			File storageRoot = new File(getAndroidFsRoot());
			return storageRoot.exists();
		} catch(Exception e) {
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
		return Environment.getExternalStorageDirectory().toString() + "/Android/data/"+ getPackageName() +"/files/";
	}
}
