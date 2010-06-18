/**
 * 
 */
package org.commcare.android.util;

import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.content.Context;

/**
 * @author ctsims
 *
 */
public class AndroidCommCarePlatform extends CommCarePlatform {
	
	private Hashtable<String, String> xmlnstable;
	private Context c;
	private ResourceTable global;
	
	private Profile profile;
	private Vector<Suite> installedSuites;

	public AndroidCommCarePlatform(int majorVersion, int minorVersion, Context c) {
		super(majorVersion, minorVersion);
		xmlnstable = new Hashtable<String, String>();
		this.c = c;
		installedSuites = new Vector<Suite>();
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
			global = ResourceTable.RetrieveTable(new SqlIndexedStorageUtility("GLOBAL_RESOURCE_TABLE", Resource.class.getName(), c), new AndroidResourceInstallerFactory());
		}
		return global;
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
}
