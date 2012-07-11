/**
 * 
 */
package org.commcare.android.tasks;

import java.util.Vector;

import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.xml.util.UnfullfilledRequirementsException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

/**
 * This task is responsible for 
 * 
 * @author ctsims
 *
 */
public class ResourceEngineTask extends AsyncTask<String, int[], Integer> implements TableStateListener {
	
	ResourceEngineListener listener;
	Context c;
	
	public static final int PHASE_CHECKING = 0;
	public static final int PHASE_DOWNLOAD = 1;
	public static final int PHASE_COMMIT = 2;
	
	Resource missingResource = null;
	int badReqCode = -1;
	private int phase = -1;  
	boolean upgradeMode = false;
	
	public static final int STATUS_INSTALLED = 0;
	public static final int STATUS_MISSING = 1;
	public static final int STATUS_ERROR = 2;
	public static final int STATUS_FAIL_UNKNOWN = 4;
	public static final int STATUS_FAIL_STATE = 8;
	public static final int STATUS_UP_TO_DATE = 16;
	
	public ResourceEngineTask(Context c, boolean upgradeMode) throws SessionUnavailableException{
		this.c = c;
		this.upgradeMode = upgradeMode;
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	protected Integer doInBackground(String... profileRefs) {
		String profileRef = profileRefs[0];
		AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
		
		try {
			//This is replicated in the application in a few places.
    		ResourceTable global = platform.getGlobalResourceTable();
    		
    		//Ok, should figure out what the state of this bad boy is.
    		Resource profile = global.getResourceWithId("commcare-application-profile");
    		
    		if(profile != null && profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
    			//We've got a fully installed profile, that's either very good, or very bad.
    			if(upgradeMode) {
    				//Good!
    			} else {
    				//Very bad!
    				return STATUS_FAIL_STATE;
    			}
    		} else {
    			//No profile.
    			if(upgradeMode) {
    				//We shouldn't have even been able to get here....
    				return STATUS_FAIL_STATE;
    			} else {
    				//Good. 
    			}
    		}
    		
    		
    		global.setStateListener(this);
    		
    		if(upgradeMode) {
    			//See where we are now
				int previousVersion = profile.getVersion();
				
				ResourceTable temporary = platform.getUpgradeResourceTable();
				temporary.setStateListener(this);

				platform.stageUpgradeTable(global, temporary, profileRef);
				phase = PHASE_CHECKING;
				platform.upgrade(global, temporary);
				
				//And see where we ended up to see whether an upgrade actually occurred
	    		Resource newProfile = global.getResourceWithId("commcare-application-profile");
	    		if(newProfile.getVersion() == previousVersion) {
	    			return STATUS_UP_TO_DATE;
	    		}
    		} else {
    			platform.init(profileRef, global, false);
    		}
    		
			//Initialize them now that they're installed
			CommCareApplication._().initializeGlobalResources();
    		
    		//Alll goood, we need to set our current profile ref to either the one
    		//just used, or the auth ref, if one is available.
    		
    		String authRef = platform.getCurrentProfile().getAuthReference() == null ? profileRef : platform.getCurrentProfile().getAuthReference();
    		
    		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
    		Editor edit = prefs.edit();
    		edit.putString("default_app_server", authRef);
    		edit.commit();
    		
    		return STATUS_INSTALLED;
		} catch (UnfullfilledRequirementsException e) {
			e.printStackTrace();
			badReqCode = e.getRequirementCode();
			
			if(!upgradeMode) {
				cleanupFailure(platform);
			}
			
			return STATUS_ERROR;
		} catch (UnresolvedResourceException e) {
			//couldn't find a resource, which isn't good. 
			e.printStackTrace();
			
			if(!upgradeMode) {
				cleanupFailure(platform);
			}
			
			missingResource = e.getResource(); 
			return STATUS_MISSING;
		} catch(Exception e) {
			e.printStackTrace();
			
			if(!upgradeMode) {
				cleanupFailure(platform);
			}
			
			return STATUS_FAIL_UNKNOWN;
		}
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
	 */
	@Override
	protected void onProgressUpdate(int[]... values) {
		super.onProgressUpdate(values);
		if(listener != null) {
			listener.updateProgress(values[0][0], values[0][1], values[0][2]);
		}
	}

	private void cleanupFailure(AndroidCommCarePlatform platform) {
		ResourceTable global = platform.getGlobalResourceTable();
		
		//Install was botched, clear anything left lying around....
		global.clear();

	}
		
	public void setListener(ResourceEngineListener listener) {
		this.listener = listener;
	}

	@Override
	protected void onPostExecute(Integer result) {
		if(listener != null) {
			if(result == STATUS_INSTALLED){
				listener.reportSuccess(true);
			} else if(result == STATUS_UP_TO_DATE){
				listener.reportSuccess(false);
			} else if(result == STATUS_MISSING){
				listener.failMissingResource(missingResource);
			} else if(result == STATUS_ERROR){
				listener.failBadReqs(badReqCode);
			} else if(result == STATUS_FAIL_STATE){
				listener.failBadState();
			} else {
				listener.failUnknown();
			}
		}
		
		//remove all references 
		listener = null;
		c = null;
	}

	public void resourceStateUpdated(ResourceTable table) {
		Vector<Resource> resources = CommCarePlatform.getResourceListFromProfile(table);
		
		//TODO: Better reflect upgrade status process
		
		int score = 0;

		for(Resource r : resources) {
			switch(r.getStatus()) {
			case Resource.RESOURCE_STATUS_UPGRADE:
				//If we spot an upgrade after we've started the upgrade process,
				//something now needs to be updated
				if(phase == PHASE_CHECKING) {
					this.phase = PHASE_DOWNLOAD;
				}
				score += 1;
				break;
			case Resource.RESOURCE_STATUS_INSTALLED:
				score += 1;
				break;
			default:
				score += 0;
				break;
			}
		}
		
		incrementProgress(score, resources.size());
	}

	public void incrementProgress(int complete, int total) {
		this.publishProgress(new int[] {complete, total, phase});
	}
	
}
