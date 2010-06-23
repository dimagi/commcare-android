/**
 * 
 */
package org.commcare.android.util;

import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.content.Context;
import android.os.Bundle;

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
	
	private String currentUser;
	private Date loginTime;
	
	private String currentCmd;
	private String currentCase;
	private String currentRef;
	private String currentForm;
	
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
			global = ResourceTable.RetrieveTable(new SqlIndexedStorageUtility<Resource>("GLOBAL_RESOURCE_TABLE", Resource.class, c), new AndroidResourceInstallerFactory());
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
	
	public void logInUser(String userId) {
		this.currentUser = userId;
		this.loginTime = new Date();
	}
	
	public String getLoggedInUser() {
		return currentUser;
	}
	
	public Vector<Entry> getEntriesForCommand(String commandId) {
		Hashtable<String,Entry> map = getMenuMap();
		Menu menu = null;
		Entry entry = null;
		top:
		for(Suite s : this.getInstalledSuites()) {
			for(Menu m : s.getMenus()) {
				//We need to see if everything in this menu can be matched
				if(currentCmd.equals(m.getId())) {
					menu = m;
					break top;
				}
				
				if(s.getEntries().containsKey(currentCmd)) {
					entry = s.getEntries().get(currentCmd);
					break top;
				}
			}
		}
		
		Vector<Entry> entries = new Vector<Entry>();
		if(entry != null) {
			entries.add(entry);
		}
		
		if(menu != null) {
			//We're in a menu we have a set of requirements which
			//need to be fulfilled
			for(String cmd : menu.getCommandIds()) {
				Entry e = map.get(cmd);
				entries.add(e);
			}
		}
		return entries;
	}
	
	public String getNeededData() {
		if(this.getCommand() == null) {
			return GlobalConstants.STATE_COMMAND_ID;
		}
		
		Vector<Entry> entries = getEntriesForCommand(this.getCommand());
		
		if(currentCase == null) {
			boolean needCase = false;
			for(Entry e : entries) {
				if(!e.getReferences().containsKey("case")){
					// We can't grab a case yet, since 
					// there is an entry which doesn't use one
					needCase = false;
					break;
				} else {
					needCase = true;
				}
			}
			if(needCase) {
				return GlobalConstants.STATE_CASE_ID;
			}
		}
		
		//the only other thing we can need is a form. If there's still
		//more than one applicable entry, we need to keep going
		if(entries.size() > 1 || !entries.elementAt(0).getCommandId().equals(this.getCommand())) {
			return GlobalConstants.STATE_COMMAND_ID;
		} else {
			return null;
		}
	}
	
	public Detail getDetail(String id) {
		for(Suite s : this.getInstalledSuites()) {
			Detail d = s.getDetail(id);
			if(d != null) {
				return d;
			}
		}
		return null;
	}

	
	public void setCaseId(String caseId) {
		this.currentCase = caseId;
	}
	
	public void setForm(String formId) {
		this.currentForm = formId;
	}
	
	public void setCommand(String commandId) {
		this.currentCmd = commandId;
	}
	
	public String getCaseId() {
		return this.currentCase;
	}
	
	public String getForm() {
		return this.currentForm;
	}
	
	public String getCommand() {
		return this.currentCmd;
	}
	
	public void clearState() {
		this.currentCase = null;
		this.currentCmd = null;
		this.currentForm = null;
		this.currentRef = null;
	}
	
	public void logout() {
		this.currentUser = null;
		this.loginTime = null;
	}
	
	public void pack(Bundle outgoing) {
		if(currentUser != null) {
			outgoing.putString(GlobalConstants.STATE_USER_KEY, currentUser);
		}
		if(loginTime != null) {
			outgoing.putLong(GlobalConstants.STATE_USER_LOGIN, loginTime.getTime());
		}
	}
	
	public void unpack(Bundle incoming) {
		if(incoming == null) {
			return;
		}
		if(incoming.containsKey(GlobalConstants.STATE_USER_LOGIN)) {
			long login = incoming.getLong(GlobalConstants.STATE_USER_LOGIN);
			double hrsSinceLogin = ((double)((new Date()).getTime() - login)/1000/60/60);
			if(hrsSinceLogin > 4) {
				loginTime = new Date(login);
				currentUser = incoming.getString(GlobalConstants.STATE_USER_KEY);
			}
		}
	}
}
