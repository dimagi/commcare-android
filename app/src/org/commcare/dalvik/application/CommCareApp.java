/**
 * 
 */
package org.commcare.dalvik.application;

import java.io.File;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.DbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.DatabaseAppOpenHelper;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.references.JavaFileRoot;
import org.commcare.android.storage.framework.Table;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.UnregisteredLocaleException;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * 
 * This (awkwardly named!) container is responsible for keeping track of a single
 * CommCare "App". It should be able to set up an App, break it back down, and 
 * maintain all of the code needed to sandbox applicaitons
 * 
 * @author ctsims
 *
 */
public class CommCareApp {
	ApplicationRecord record;
	
	JavaFileRoot fileRoot;
	AndroidCommCarePlatform platform;
	
	public static Object lock = new Object();
	
	/** This unfortunately can't be managed entirely by the application object, so we have to do some here **/
	public static CommCareApp currentSandbox;
	
	private Object appDbHandleLock = new Object();
	private SQLiteDatabase appDatabase; 
	
	public CommCareApp(ApplicationRecord record) {
		this.record = record;
		
		//Now, we need to identify the state of the application resources
		int[] version = CommCareApplication._().getCommCareVersion();

		//TODO: Badly coupled
		platform = new AndroidCommCarePlatform(version[0], version[1], CommCareApplication._(), this);
	}
	
	public String storageRoot() {
		//This External Storage Directory will always destroy your data when you upgrade, which is stupid. Unfortunately
		//it's also largely unavoidable until Froyo's fix for this problem makes it to the phones. For now we're going
		//to rely on the fact that the phone knows how to fix missing/corrupt directories every time it upgrades.
		return CommCareApplication._().getAndroidFsRoot() + "app/" + record.getApplicationId() + "/";
	}
	
    private void createPaths() {
    	String[] paths = new String[] {"", GlobalConstants.FILE_CC_INSTALL, GlobalConstants.FILE_CC_UPGRADE, GlobalConstants.FILE_CC_CACHE, GlobalConstants.FILE_CC_FORMS, GlobalConstants.FILE_CC_MEDIA, GlobalConstants.FILE_CC_LOGS};
    	for(String path : paths) {
    		File f = new File(fsPath(path));
    		if(!f.exists()) {
    			f.mkdirs();
    		}
    	}
    }
    
	public String fsPath(String relative) {
		return storageRoot() + relative;
	}

	
	public void initializeFileRoots() {
		fileRoot = new JavaFileRoot(storageRoot());
		ReferenceManager._().addReferenceFactory(fileRoot);
	}
	
	public SharedPreferences getAppPreferences() {
		return CommCareApplication._().getSharedPreferences(getPreferencesFilename(), 0);
	}
	
	public void setupSandbox() {
		synchronized(lock) {
			if(currentSandbox != null && currentSandbox != this) {
				currentSandbox.teardownSandbox();
			}
			//general setup
			createPaths();
			initializeFileRoots();
			currentSandbox = this;
		}
	}
	
	
	public boolean initializeApplication() {
		setupSandbox();

		ResourceTable global = platform.getGlobalResourceTable();
		//TODO: This, but better.
		Resource profile = global.getResourceWithId("commcare-application-profile");
		if(profile != null && profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
			platform.initialize(global);
			try{
				Localization.setLocale(getAppPreferences().getString("cur_locale", "default"));
			}catch(UnregisteredLocaleException urle) {
				Localization.setLocale(Localization.getGlobalLocalizerAdvanced().getAvailableLocales()[0]);
			}
			return true;
		}
		return false;
	}
	
	
	public boolean areResourcesValidated(){
		SharedPreferences appPreferences = getAppPreferences();
		return appPreferences.getBoolean("isValidated",false) || appPreferences.getString(CommCarePreferences.CONTENT_VALIDATED, "no").equals(CommCarePreferences.YES);
	}
	
	public void setResourcesValidated(boolean isValidated){
		SharedPreferences.Editor editor = getAppPreferences().edit();
		editor.putBoolean("isValidated", isValidated);
		editor.commit();
	}
	
	public void teardownSandbox() {	
		synchronized(lock) {
			ReferenceManager._().removeReferenceFactory(fileRoot);
			
			synchronized(appDbHandleLock) {
				if(appDatabase != null) {
					appDatabase.close();
				}
				appDatabase = null;
			}
		}
	}

	public AndroidCommCarePlatform getCommCarePlatform() {
		return platform;
	}
	

	public <T extends Persistable> SqlStorage<T> getStorage(Class<T> c) throws SessionUnavailableException {
		return getStorage(c.getAnnotation(Table.class).value(), c); 
	}
	
	public <T extends Persistable> SqlStorage<T> getStorage(String name, Class<T> c) throws SessionUnavailableException {
		return new SqlStorage<T>(name, c, new DbHelper(CommCareApplication._().getApplicationContext()){
			@Override
			public SQLiteDatabase getHandle() {
				synchronized(appDbHandleLock) {
					if(appDatabase == null || !appDatabase.isOpen()) {
						appDatabase = new DatabaseAppOpenHelper(this.c, record.getApplicationId()).getWritableDatabase(null);
					}
					return appDatabase;
				}
			}
		});
	}

	public void clearInstallData() {
		ResourceTable global = platform.getGlobalResourceTable();
		
		//Install was botched, clear anything left lying around....
		global.clear();
	}

	public void writeInstalled() {
		record.setStatus(ApplicationRecord.STATUS_INSTALLED);
		try {
			CommCareApplication._().getGlobalStorage(ApplicationRecord.class).write(record);
		} catch (StorageFullException e) {
			throw new RuntimeException(e);
		}
	}
	
	public String getPreferencesFilename(){
		return record.getApplicationId();
	}
}
