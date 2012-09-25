/**
 * 
 */
package org.commcare.android.tasks;

import java.util.Date;
import java.util.Vector;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.Logger;

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
public class ResourceEngineTask extends AsyncTask<String, int[], org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes> implements TableStateListener {
	
	public enum ResourceEngineOutcomes implements MessageTag {
		/** App installed Succesfully **/
		StatusInstalled("notification.install.installed"),
		
		/** Missing resources could not be found during install **/
		StatusMissing("notification.install.missing"),
		
		/** App is not compatible with current installation **/
		StatusBadReqs("notification.install.badreqs"),
		
		/** Unknown Error **/
		StatusFailUnknown("notification.install.unknown"),
		
		/** There's already an app installed **/
		StatusFailState("notification.install.badstate"),
		
		/** There's already an app installed **/
		StatusNoLocalStorage("notification.install.nolocal"),
		
		/** Install is fine **/
		StatusUpToDate("notification.install.uptodate");
		
		ResourceEngineOutcomes(String root) {this.root = root;}
		private final String root;
		public String getLocaleKeyBase() { return root;}
		public String getCategory() { return "install_update"; }
		
	}

	
	ResourceEngineListener listener;
	Context c;
	
	public static final int PHASE_CHECKING = 0;
	public static final int PHASE_DOWNLOAD = 1;
	public static final int PHASE_COMMIT = 2;
	
	Resource missingResource = null;
	int badReqCode = -1;
	private int phase = -1;  
	boolean upgradeMode = false;
	
	String vAvailable;
	String vRequired;
	boolean majorIsProblem;
	
	public ResourceEngineTask(Context c, boolean upgradeMode) throws SessionUnavailableException{
		this.c = c;
		this.upgradeMode = upgradeMode;
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	protected ResourceEngineOutcomes doInBackground(String... profileRefs) {
		String profileRef = profileRefs[0];
		AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		

		//First of all, make sure we record that an attempt was started.
		Editor editor = prefs.edit();
		editor.putLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, new Date().getTime());
		editor.commit();
		
		if(upgradeMode) {
			Logger.log(AndroidLogger.TYPE_RESOURCES, "Beginning upgrade attempt for profile " + profileRefs[0]);
		} else {
			Logger.log(AndroidLogger.TYPE_RESOURCES, "Beginning install attempt for profile " + profileRefs[0]);
		}
		
		
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
    				return ResourceEngineOutcomes.StatusFailState;
    			}
    		} else {
    			//No profile.
    			if(upgradeMode) {
    				//We shouldn't have even been able to get here....
    				return ResourceEngineOutcomes.StatusFailState;
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
	    			Logger.log(AndroidLogger.TYPE_RESOURCES, "App Resources up to Date");
	    			return ResourceEngineOutcomes.StatusUpToDate;
	    		}
    		} else {
    			platform.init(profileRef, global, false);
    		}
    		
			//Initialize them now that they're installed
			CommCareApplication._().initializeGlobalResources();
    		
    		//Alll goood, we need to set our current profile ref to either the one
    		//just used, or the auth ref, if one is available.
    		
    		String authRef = platform.getCurrentProfile().getAuthReference() == null ? profileRef : platform.getCurrentProfile().getAuthReference();
    		
    		prefs = PreferenceManager.getDefaultSharedPreferences(c);
    		Editor edit = prefs.edit();
    		edit.putString("default_app_server", authRef);
    		edit.commit();
    		
    		return ResourceEngineOutcomes.StatusInstalled;
		} catch (LocalStorageUnavailableException e) {
			e.printStackTrace();
			if(!upgradeMode) {
				cleanupFailure(platform);
			}
			
			Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Couldn't install file to local storage|" + e.getMessage());
			return ResourceEngineOutcomes.StatusNoLocalStorage;
		}catch (UnfullfilledRequirementsException e) {
			e.printStackTrace();
			badReqCode = e.getRequirementCode();
			
			vAvailable = e.getAvailableVesionString();
			vRequired= e.getRequiredVersionString();
			majorIsProblem = e.getRequirementCode() == UnfullfilledRequirementsException.REQUIREMENT_MAJOR_APP_VERSION;
			
			if(!upgradeMode) {
				cleanupFailure(platform);
			}
			Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "App resources are incompatible with this device|" + e.getMessage());
			return ResourceEngineOutcomes.StatusBadReqs;
		} catch (UnresolvedResourceException e) {
			//couldn't find a resource, which isn't good. 
			e.printStackTrace();
			
			if(!upgradeMode) {
				cleanupFailure(platform);
			}
			
			missingResource = e.getResource(); 
			Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "A resource couldn't be found, almost certainly due to the network|" + e.getMessage());
			return ResourceEngineOutcomes.StatusMissing;
		} catch(Exception e) {
			e.printStackTrace();
			
			if(!upgradeMode) {
				cleanupFailure(platform);
			}
			
			Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Unknown error ocurred during install|" + e.getMessage());
			return ResourceEngineOutcomes.StatusFailUnknown;
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
	protected void onPostExecute(ResourceEngineOutcomes result) {
		if(listener != null) {
			if(result == ResourceEngineOutcomes.StatusInstalled){
				listener.reportSuccess(true);
			} else if(result == ResourceEngineOutcomes.StatusUpToDate){
				listener.reportSuccess(false);
			} else if(result == ResourceEngineOutcomes.StatusMissing){
				listener.failMissingResource(missingResource, ResourceEngineOutcomes.StatusMissing);
			} else if(result == ResourceEngineOutcomes.StatusBadReqs){
				listener.failBadReqs(badReqCode, vRequired, vAvailable, majorIsProblem);
			} else if(result == ResourceEngineOutcomes.StatusFailState){
				listener.failWithNotification(ResourceEngineOutcomes.StatusFailState);
			} else if(result == ResourceEngineOutcomes.StatusNoLocalStorage) {
				listener.failWithNotification(ResourceEngineOutcomes.StatusNoLocalStorage);
			} else {
				listener.failUnknown(ResourceEngineOutcomes.StatusFailUnknown);
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
