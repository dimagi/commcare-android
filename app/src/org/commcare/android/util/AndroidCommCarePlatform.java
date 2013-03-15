/**
 * 
 */
package org.commcare.android.util;

import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/**
 * @author ctsims
 *
 */
public class AndroidCommCarePlatform extends CommCarePlatform {
	
	public static final String ENTITY_NONE = "NONE";
	public static final String STATE_REFERRAL_TYPE = "REFERRAL_TYPE";
	
	private Hashtable<String, String> xmlnstable;
	private Context c;
	private ResourceTable global;
	private ResourceTable upgrade;
	private ResourceTable recovery;
	
	private Profile profile;
	private Vector<Suite> installedSuites;
	CommCareApp app;
	
	private long callDuration = 0;
	
	public AndroidCommCarePlatform(int majorVersion, int minorVersion, Context c, CommCareApp app) {
		super(majorVersion, minorVersion);
		xmlnstable = new Hashtable<String, String>();
		this.c = c;
		installedSuites = new Vector<Suite>();
		this.app = app;
	}
	
	public void registerXmlns(String xmlns, String filepath) {
		xmlnstable.put(xmlns, filepath);
	}
	
	public Set<String> getInstalledForms() {
		return xmlnstable.keySet();
	}

	public Uri getFormContentUri(String xFormNamespace) {
		if(xmlnstable.containsKey(xFormNamespace)) {
			return Uri.parse(xmlnstable.get(xFormNamespace));
		} 
		
		//Search through manually?
		return null;
	}
	
	public ResourceTable getGlobalResourceTable() {
		if(global == null) {
			global = ResourceTable.RetrieveTable( app.getStorage("GLOBAL_RESOURCE_TABLE", Resource.class), new AndroidResourceInstallerFactory(app));
		}
		return global;
	}
	
	public ResourceTable getUpgradeResourceTable() {
		if(upgrade == null) {
			upgrade = ResourceTable.RetrieveTable( app.getStorage("UPGRADE_RESOURCE_TABLE", Resource.class), new AndroidResourceInstallerFactory(app));
		}
		return upgrade;
	}
	
	public ResourceTable getRecoveryTable() {
		if(recovery == null) {
			recovery = ResourceTable.RetrieveTable( app.getStorage("RECOVERY_RESOURCE_TABLE", Resource.class), new AndroidResourceInstallerFactory(app));
		}
		return recovery;
	}
	
	public Profile getCurrentProfile() {
		return profile;
	}
	
	public Vector<Suite> getInstalledSuites() {
		return installedSuites;
	}
	
	public void setProfile(Profile p) {
		this.profile = p;
	}
	
	
	public void registerSuite(Suite s) {
		this.installedSuites.add(s);
	}
	
	
	public void pack(Bundle outgoing) {

	}
	
	public void unpack(Bundle incoming) {
		if(incoming == null) {
			return;
		}
	}

	public void setCallDuration(long callDuration) {
		this.callDuration = callDuration;
	}
	
	public long getCallDuration() {
		return callDuration;
	}
	
	public void initialize(ResourceTable global) {
		this.profile = null;
		this.installedSuites.clear();
		super.initialize(global);
	}

	public IStorageUtilityIndexed<FormInstance> getFixtureStorage() {
		return app.getStorage("fixture", FormInstance.class);
	}	
}
