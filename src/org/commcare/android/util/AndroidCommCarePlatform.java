/**
 * 
 */
package org.commcare.android.util;

import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.User;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareSession;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.content.Context;
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
	
	private Profile profile;
	private Vector<Suite> installedSuites;
	
	private long callDuration = 0;
	
	private CommCareSession session;
	
	public AndroidCommCarePlatform(int majorVersion, int minorVersion, Context c) {
		super(majorVersion, minorVersion);
		xmlnstable = new Hashtable<String, String>();
		this.c = c;
		installedSuites = new Vector<Suite>();
		session = new CommCareSession(this);
	}
	
	public void registerXmlns(String xmlns, String filepath) {
		xmlnstable.put(xmlns, filepath);
	}

	public String getFormPath(String xFormNamespace) {
		if(xmlnstable.containsKey(xFormNamespace)) {
			try {
				return ReferenceManager._().DeriveReference(xmlnstable.get(xFormNamespace)).getLocalURI();
			} catch (InvalidReferenceException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		} 
		
		//Search through manually?
		return null;
	}
	
	public ResourceTable getGlobalResourceTable() {
		if(global == null) {
			global = ResourceTable.RetrieveTable( CommCareApplication._().getStorage("GLOBAL_RESOURCE_TABLE", Resource.class), new AndroidResourceInstallerFactory());
		}
		return global;
	}
	
	public ResourceTable getUpgradeResourceTable() {
		if(upgrade == null) {
			upgrade = ResourceTable.RetrieveTable( CommCareApplication._().getStorage("UPGRADE_RESOURCE_TABLE", Resource.class), new AndroidResourceInstallerFactory());
		}
		return upgrade;
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
	
	public CommCareSession getSession() {
		return session;
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
}
