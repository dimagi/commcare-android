/**
 * 
 */
package org.commcare.dalvik.application;

import java.io.File;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.references.JavaFileRoot;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;

import android.content.SharedPreferences;
import android.os.Environment;

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
	
	public CommCareApp(ApplicationRecord record) {
		this.record = record;
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
	
	
	public boolean initializeApplication() {
		//general setup
		createPaths();
		initializeFileRoots();
		
		
		//Now, we need to identify the state of the application resources
		int[] version = CommCareApplication._().getCommCareVersion();
		platform = new AndroidCommCarePlatform(version[0], version[1], CommCareApplication._());

		ResourceTable global = platform.getGlobalResourceTable();
		
		//TODO: This, but better.
		Resource profile = global.getResourceWithId("commcare-application-profile");
		if(profile != null && profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
			platform.initialize(global);
			Localization.setLocale(Localization.getGlobalLocalizerAdvanced().getAvailableLocales()[0]);
			return true;
		}
		return false;
	}
	
	
	public boolean areResourcesValidated(){
		return appPreferences.getBoolean("isValidated",false) || appPreferences.getString(CommCarePreferences.CONTENT_VALIDATED, "no").equals(CommCarePreferences.YES);
	}
	
	public void setResourcesValidated(boolean isValidated){
		SharedPreferences.Editor editor = appPreferences.edit();
		editor.putBoolean("isValidated", isValidated);
		editor.commit();
	}
	
	public void teardown() {	
		ReferenceManager._().removeReferenceFactory(fileRoot);
	}

	public AndroidCommCarePlatform getCommCarePlatform() {
		return platform;
	}
}
