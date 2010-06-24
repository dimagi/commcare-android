/**
 * 
 */
package org.commcare.android.util;

import java.io.File;

import org.commcare.android.R;
import org.commcare.android.database.TableBuilder;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.Case;
import org.commcare.android.models.Referral;
import org.commcare.android.models.User;
import org.commcare.android.preferences.ServerPreferences;
import org.commcare.android.references.JavaFileRoot;
import org.commcare.android.references.JavaHttpRoot;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.reference.ReferenceFactory;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.IStorageFactory;
import org.javarosa.core.services.storage.IStorageUtility;
import org.javarosa.core.services.storage.StorageManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.preference.PreferenceManager;

/**
 * @author ctsims
 *
 */
public class CommCarePlatformProvider {
	private static AndroidCommCarePlatform platform;	
	
	private static ReferenceFactory http;
	private static ReferenceFactory file;
	
	public static AndroidCommCarePlatform unpack(Bundle incoming, Context c) {
		if(platform ==null) {
			platform = initData(c);
		}
		platform.unpack(incoming);
		return platform;
	}
	
	public static void pack(Bundle outgoing, AndroidCommCarePlatform platform) {
		CommCarePlatformProvider.platform = platform;
		platform.pack(outgoing);
	}
	
	private static AndroidCommCarePlatform initData(Context c) {
    	int[] version = getVersion(c);
    	AndroidCommCarePlatform newplatform = new AndroidCommCarePlatform(version[0], version[1], c);
        
        createPaths();
        setRoots();
        
        
        initDb(c);
		
		//All of the below is on account of the fact that the installers 
		//aren't going through a factory method to handle them differently
		//per device.
		StorageManager.setStorageFactory(new IStorageFactory() {

			public IStorageUtility newStorage(String name, Class type) {
				return new DummyIndexedStorageUtility();
			}
			
		});
		
		PropertyManager.setPropertyManager(new ODKPropertyManager());
		
		ResourceTable global = newplatform.getGlobalResourceTable();
		
		try {
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(c);
    		String profile = settings.getString(ServerPreferences.KEY_APP, c.getString(R.string.default_app_server));
			newplatform.init(profile, global, false);
		} catch (UnfullfilledRequirementsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		newplatform.initialize(global);
		Localization.setLocale(Localization.getGlobalLocalizerAdvanced().getAvailableLocales()[0]);
		
		return newplatform;
    }
    
    private static void createPaths() {
    	String[] paths = new String[] {GlobalConstants.FILE_CC_ROOT, GlobalConstants.FILE_CC_INSTALL, GlobalConstants.FILE_CC_UPGRADE, GlobalConstants.FILE_CC_CACHE, GlobalConstants.FILE_CC_SAVED, GlobalConstants.FILE_CC_PROCESSED};
    	for(String path : paths) {
    		File f = new File(path);
    		if(!f.exists()) {
    			f.mkdir();
    		}
    	}
    }
    
	private static void setRoots() {
		if(http == null) {
			http = new JavaHttpRoot();
		}
		if(file == null) {
			file = new JavaFileRoot(GlobalConstants.FILE_REF_ROOT);
		}
		
		ReferenceManager._().addReferenceFactory(http);
		ReferenceManager._().addReferenceFactory(file);
		ReferenceManager._().addRootTranslator(new RootTranslator("jr://resource/",GlobalConstants.RESOURCE_PATH));
	}

	private static int versionCode(Context c) {
		try {
			PackageManager pm = c.getPackageManager();
			PackageInfo pi = pm.getPackageInfo("org.commcare.android", 0);
			return pi.versionCode;
		} catch(NameNotFoundException e) {
			throw new RuntimeException("Android package name not available.");
		}
	}
    
	private static int[] getVersion(Context c) {
	    return versionNumbers(versionCode(c));
	}
	
	private static int[] versionNumbers(int versionCode) {
		if(versionCode == 1) {
			return new int[] {1, 0};
		} else {
			return new int[] {-1, -1};
		}
	}
	
	private static void initDb(Context c) {
		SQLiteDatabase database;
		try {
			database = SQLiteDatabase.openDatabase(GlobalConstants.DB_LOCATION, null, SQLiteDatabase.OPEN_READWRITE);
		} catch(SQLiteException e) {
			//No database
			database = createDataBase(c);
		}
		database.close();
	}
	
	private static SQLiteDatabase createDataBase(Context c) {
		SQLiteDatabase database = SQLiteDatabase.openDatabase(GlobalConstants.DB_LOCATION, null, SQLiteDatabase.CREATE_IF_NECESSARY);
		try{
			
			database.beginTransaction();
			database.setVersion(versionCode(c));
			
			TableBuilder builder = new TableBuilder(Case.STORAGE_KEY);
			builder.addData(new Case());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(Referral.STORAGE_KEY);
			builder.addData(new Referral());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(User.STORAGE_KEY);
			builder.addData(new User());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder("GLOBAL_RESOURCE_TABLE");
			builder.addData(new Resource());
			database.execSQL(builder.getTableCreateString());
			
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
		return database;
	}

}
