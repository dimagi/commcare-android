/**
 * 
 */
package org.commcare.android.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import org.commcare.android.R;
import org.commcare.android.database.CommCareDBCursorFactory;
import org.commcare.android.database.CommCareOpenHelper;
import org.commcare.android.database.DbHelper;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.EncryptedModel;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.SqlStorageIterator;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.ACase;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.SessionStateDescriptor;
import org.commcare.android.models.User;
import org.commcare.android.references.JavaFileRoot;
import org.commcare.android.references.JavaHttpRoot;
import org.commcare.android.services.CommCareSessionService;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidSessionWrapper;
import org.commcare.android.util.CallInPhoneListener;
import org.commcare.android.util.CommCareExceptionHandler;
import org.commcare.android.util.ODKPropertyManager;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCareSession;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.Externalizable;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

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
	
	private AndroidCommCarePlatform platform;
	
	private AndroidSessionWrapper sessionWrapper;
	
	private static SQLiteDatabase database; 
	
	private SharedPreferences appPreferences;

	@Override
	public void onCreate() {
		super.onCreate();
		
		//Workaround because android is written by 7 year olds.
		//(reuses http connection pool improperly, so the second https
		//request in a short time period will flop)
		System.setProperty("http.keepAlive", "false");
		
		Thread.setDefaultUncaughtExceptionHandler(new CommCareExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()));
		
		PropertyManager.setPropertyManager(new ODKPropertyManager());
		
		
		createPaths();
		setRoots();
		
		CommCareApplication.app = this;
		
        appPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
		int[] version = getCommCareVersion();
		platform = new AndroidCommCarePlatform(version[0], version[1], this);
		
		//The fallback in case the db isn't installed 
		resourceState = STATE_UNINSTALLED;
		
		initializeGlobalResources();
		
		if(dbState != STATE_UNINSTALLED) {
			CursorFactory factory = new CommCareDBCursorFactory();
			synchronized(dbHandleLock) {
				database = new CommCareOpenHelper(this, factory).getWritableDatabase();
			}
		}
	}
	
	public void logout() {
		if(this.sessionWrapper != null) {
			sessionWrapper.reset();
		}
        
        doUnbindService();
	}
	
	public void logIn(byte[] symetricKey, User user) {
		doBindService(symetricKey, user);
	}
	
	public SecretKey createNewSymetricKey() {
		synchronized(mBoundService) {
			return mBoundService.createNewSymetricKey();
		}
	}
	
	private CallInPhoneListener listener = null;
	
	private void attachCallListener(User user) {
		TelephonyManager tManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
		
		listener = new CallInPhoneListener(this, this.getCommCarePlatform(), user);
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
			PackageInfo pi = pm.getPackageInfo("org.commcare.android", 0);
			return pi.versionCode;
		} catch(NameNotFoundException e) {
			throw new RuntimeException("Android package name not available.");
		}
	}
    
	public int[] getCommCareVersion() {
		return this.getResources().getIntArray(R.array.commcare_version);
	}

	public AndroidCommCarePlatform getCommCarePlatform() {
		return platform;
	}
	
	public CommCareSession getCurrentSession() {
		return sessionWrapper.getSession();
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
	
	public void initializeGlobalResources() {
		dbState = initDb();
		if(dbState != STATE_UNINSTALLED) {
			resourceState = initResources();
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
	
	public SharedPreferences preferences() {
		return appPreferences;
	}
	
	public String fsPath(String relative) {
		return storageRoot() + relative;
	}
    
    private void createPaths() {
    	String[] paths = new String[] {GlobalConstants.FILE_CC_ROOT, GlobalConstants.FILE_CC_INSTALL, GlobalConstants.FILE_CC_UPGRADE, GlobalConstants.FILE_CC_CACHE, GlobalConstants.FILE_CC_SAVED, GlobalConstants.FILE_CC_PROCESSED, GlobalConstants.FILE_CC_INCOMPLETE, GlobalConstants.FILE_CC_STORED, GlobalConstants.FILE_CC_MEDIA};
    	for(String path : paths) {
    		File f = new File(fsPath(path));
    		if(!f.exists()) {
    			f.mkdirs();
    		}
    	}
    }
	
	private void setRoots() {
		JavaHttpRoot http = new JavaHttpRoot();
		
		JavaFileRoot file = new JavaFileRoot(storageRoot());

		ReferenceManager._().addReferenceFactory(http);
		ReferenceManager._().addReferenceFactory(file);
		//ReferenceManager._().addRootTranslator(new RootTranslator("jr://resource/",GlobalConstants.RESOURCE_PATH));
		ReferenceManager._().addRootTranslator(new RootTranslator("jr://media/",GlobalConstants.MEDIA_REF));
	}
	
	private String storageRoot() {
		//This External Storage Directory will always destroy your data when you upgrade, which is stupid. Unfortunately
		//it's also largely unavoidable until Froyo's fix for this problem makes it to the phones. For now we're going
		//to rely on the fact that the phone knows how to fix missing/corrupt directories every time it upgrades.
		return Environment.getExternalStorageDirectory().toString() + "/Android/data/"+ this.getPackageName() +"/files/";
	}
	
	private int initResources() {
		try {
			
			//Now, we need to identify the state of the application resources
			AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform(); 
			ResourceTable global = platform.getGlobalResourceTable();
			//TODO: This, but better.
			Resource profile = global.getResourceWithId("commcare-application-profile");
			if(profile != null && profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
				platform.initialize(global);
				Localization.setLocale(Localization.getGlobalLocalizerAdvanced().getAvailableLocales()[0]);
				return STATE_READY;
			} else{
				return STATE_UNINSTALLED;
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

	private int initDb() {
		SQLiteDatabase database;
		try {
			database = new CommCareOpenHelper(this).getWritableDatabase();
			database.close();
			return STATE_READY;
		} catch(SQLiteException e) {
			//Only thrown in DB isn't there
			return STATE_UNINSTALLED;
		}
	}
	
	public Object dbHandleLock = new Object();
	
	public <T extends Persistable> SqlIndexedStorageUtility<T> getStorage(String storage, Class<T> c) throws SessionUnavailableException {
		DbHelper helper = null;
		try {
			//If the session service is available, go grab it
			CommCareSessionService service = getSession();
			if(service.isLoggedIn()) {
				helper = new DbHelper(this.getApplicationContext(), service.getEncrypter()) {
					@Override
					public SQLiteDatabase getHandle() {
						synchronized(dbHandleLock) {
							if(database == null || !database.isOpen()) {
								CursorFactory factory = new CommCareDBCursorFactory(encryptedModels()) {
									protected Cipher getReadCipher() {
										return getSession().getDecrypter();
									}
								};
								database = (new CommCareOpenHelper(this.c, factory)).getWritableDatabase();
							}
							return database;
						}
					}
					
				};
			}
		} catch(SessionUnavailableException sue) {
			//Not logged in, proceed with unencrypted storage
		}
		
		//If we didn't get the decrypting DB helper, we should grab the normal one
		if(helper == null) {
			helper = new DbHelper(this.getApplicationContext()) {
				@Override
				public SQLiteDatabase getHandle() {
					synchronized(dbHandleLock) {
						if(database == null || !database.isOpen()) {
							CursorFactory factory = new CommCareDBCursorFactory();
							database = new CommCareOpenHelper(this.c, factory).getWritableDatabase();
						}
						return database;
					}
				}
				
			};
		}
		return new SqlIndexedStorageUtility<T>(storage, c, helper);
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
	
	private Hashtable<String, EncryptedModel> encryptedModels() {
		Hashtable<String, EncryptedModel> models = new Hashtable<String, EncryptedModel>();
		models.put(ACase.STORAGE_KEY, new ACase());
		models.put(FormRecord.STORAGE_KEY, new ACase());
		return models;
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
	 * This method is a shortcut to wiping out the profile/suite/xforms/etc, and 
	 * regathering the application resources in the case of something bad happening.
	 * 
	 * It may mess up the app, so it shouldn't be called upon trivially.
	 */
	public boolean resetApplicationResources() {
		ResourceTable global = platform.getGlobalResourceTable();
		global.clear();
		String profile = appPreferences.getString("default_app_server", this.getString(R.string.default_app_server));
		try {
			platform.init(profile, global, false);
			resourceState = this.initResources();
			return true;
		} catch (UnfullfilledRequirementsException e) {
			ExceptionReportTask ert = new ExceptionReportTask();
			ert.execute(e);
			e.printStackTrace();
			return false;
		} catch (UnresolvedResourceException e) {
			ExceptionReportTask ert = new ExceptionReportTask();
			ert.execute(e);
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * This method goes through and identifies whether there are elements in the
	 * database which point to/expect files to exist on the file system, and clears
	 * out any records which refer to files that don't exist. 
	 */
	public void cleanUpDatabaseFileLinkages() throws SessionUnavailableException{
		Vector<Integer> toDelete = new Vector<Integer>();
		
		SqlIndexedStorageUtility<FormRecord> storage = getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		
		//Can't load the records outright, since we'd need to be logged in (The key is encrypted)
		for(SqlStorageIterator iterator = storage.iterate(); iterator.hasMore();) {
			int id = iterator.nextID();
			String path = storage.getMetaDataFieldForRecord(id, FormRecord.META_PATH);
			if(!new File(path).exists()) {
				toDelete.add(id);
			}
		}
		
		for(int recordid : toDelete) {
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
		//First clear anything that will require the user's key, since we're going to wipe it out!
		getStorage(ACase.STORAGE_KEY, ACase.class).removeAll();
		
		//TODO: We should really be wiping out the _stored_ instances here, too
		getStorage(FormRecord.STORAGE_KEY, FormRecord.class).removeAll();
		
		//Also, any of the sessions we've got saved
		getStorage(SessionStateDescriptor.STORAGE_KEY, SessionStateDescriptor.class).removeAll();
		
		//Now we wipe out the user entirely
		getStorage(User.STORAGE_KEY, User.class).removeAll();
		
		//Get rid of any user fixtures
		getStorage("fixture", FormInstance.class).removeAll();
		
		
		
		//Should be good to go. The app'll log us out now that there's no user details in memory
		logout();
	}

	public String getCurrentVersionString() {
		PackageManager pm = this.getPackageManager();
		PackageInfo pi;
		try {
			pi = pm.getPackageInfo("org.commcare.android", 0);
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
		
		String buildDate = getString(R.string.app_build_date);
		String buildNumber = getString(R.string.app_build_number);
		
		return "CommCare ODK, version \"" + pi.versionName + "\"(" + pi.versionCode+ "). CommCare Version " +  ccv + ". Build #" + buildNumber + ", built on: " + buildDate;
		
	}
	
	//Start Service code. Will be changed in the future
	private CommCareSessionService mBoundService;

	private ServiceConnection mConnection;

	boolean mIsBound = false;
	boolean mIsBinding = false;
	void doBindService(final byte[] key, final User user) {
		mConnection = new ServiceConnection() {
		    public void onServiceConnected(ComponentName className, IBinder service) {
		        // This is called when the connection with the service has been
		        // established, giving us the service object we can use to
		        // interact with the service.  Because we have bound to a explicit
		        // service that we know is running in our own process, we can
		        // cast its IBinder to a concrete class and directly access it.
		        mBoundService = ((CommCareSessionService.LocalBinder)service).getService();
		        
				synchronized(mBoundService) {
					//Don't let anyone touch this until it's logged in
					mBoundService.logIn(key, user);
				}

		        synchronized(dbHandleLock) {
		        	if(database != null && database.isOpen()) {
						database.close();
					}
		        	
		        	//NOTE: If any of this is updated care should be taken to ensure that none of it depends on
		        	//the mIsBound service flag, otherwise we could deadlock
					CursorFactory factory = new CommCareDBCursorFactory(CommCareApplication.this.encryptedModels()) {
						protected Cipher getReadCipher() {
							return mBoundService.getDecrypter();
						}
					};
					database = new CommCareOpenHelper(CommCareApplication.this, factory).getWritableDatabase();
				}
		        
				//service available
			    mIsBound = true;
		        
		        //Don't signal bind completion until the db is initialized.
			    mIsBinding = false;
			    
				if(user != null) {
					attachCallListener(user);
					CommCareApplication.this.sessionWrapper = new AndroidSessionWrapper(CommCareApplication.this.getCommCarePlatform());
				}
//				for(String xmlns : platform.getInstalledForms()) {
//					Uri formURI = platform.getFormContentUri(xmlns);
//					Cursor c = CommCareApplication.this.getContentResolver().query(formURI, new String[] {FormsColumns.BASE64_RSA_PUBLIC_KEY } ,null, null, null);
//					if(!c.moveToFirst()) {
//						// Soooooo bad.
//						continue;
//					} else {
//						//Projection ensures we only have one column here.
//						//if(c.isNull(0)) {
//							ContentValues cv = new ContentValues();
//							PublicKey pk = mBoundService.generateKeyPair(xmlns);
//							String encodedKey = Base64.encode(pk.getEncoded());
//							cv.put(FormsColumns.BASE64_RSA_PUBLIC_KEY, encodedKey);
//							CommCareApplication.this.getContentResolver().update(formURI, cv, null, null);
//						//}
//					}
//				}
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

	void doUnbindService() {
	    if (mIsBound) {
	    	mBoundService.logout();
	        // Detach our existing connection.
	        unbindService(mConnection);
	        mIsBound = false;
	    }
	}
	
	public CommCareSessionService getSession() throws SessionUnavailableException {
		//If binding is currently in process, just wait for it.
		while(mIsBinding);
		
		if(mIsBound) {
			synchronized(mBoundService) {
				return mBoundService;
			}
		} else {
			throw new SessionUnavailableException();
		}
	}

}
