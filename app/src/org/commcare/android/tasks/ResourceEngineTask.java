/**
 * 
 */
package org.commcare.android.tasks;

import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Vector;

import javax.net.ssl.SSLHandshakeException;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApp;
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

/**
 * This task is responsible for 
 * 
 * @author ctsims
 *
 */
public abstract class ResourceEngineTask<R> extends CommCareTask<String, int[], org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes, R> implements TableStateListener {
	
	public enum ResourceEngineOutcomes implements MessageTag {
		/** App installed Succesfully **/
		StatusInstalled("notification.install.installed"),
		
		/** Missing resources could not be found during install **/
		StatusMissing("notification.install.missing"),
		
		/** Missing resources could not be found during install **/
		StatusMissingDetails("notification.install.missing.withmessage"),
		
		/** App is not compatible with current installation **/
		StatusBadReqs("notification.install.badreqs"),
		
		/** Unknown Error **/
		StatusFailUnknown("notification.install.unknown"),
		
		/** There's already an app installed **/
		StatusFailState("notification.install.badstate"),
		
		/** There's already an app installed **/
		StatusNoLocalStorage("notification.install.nolocal"),
		
		/** Install is fine **/
		StatusUpToDate("notification.install.uptodate"),
		
		/** Certificate was bad **/
		StatusBadCertificate("notification.install.badcert");
		
		ResourceEngineOutcomes(String root) {this.root = root;}
		private final String root;
		public String getLocaleKeyBase() { return root;}
		public String getCategory() { return "install_update"; }
		
	}
	
	Context c;
	CommCareApp app;
	
	public static final int PHASE_CHECKING = 0;
	public static final int PHASE_DOWNLOAD = 1;
	public static final int PHASE_COMMIT = 2;
	
	protected UnresolvedResourceException missingResourceException = null;
	protected int badReqCode = -1;
	protected int phase = -1;  
	boolean upgradeMode = false;
	boolean partialMode = false;
	boolean startOverUpgrade;
	
	protected String vAvailable;
	protected String vRequired;
	protected boolean majorIsProblem;
	
	public ResourceEngineTask(Context c, boolean upgradeMode, boolean partialMode, CommCareApp app, boolean startOverUpgrade, int taskId) throws SessionUnavailableException{
		this.partialMode = partialMode;
		this.c = c;
		this.upgradeMode = upgradeMode;
		this.app = app;
		this.startOverUpgrade = startOverUpgrade;
		this.taskId = taskId;
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	protected ResourceEngineOutcomes doTaskBackground(String... profileRefs) {
		String profileRef = profileRefs[0];
		AndroidCommCarePlatform platform = app.getCommCarePlatform();
		SharedPreferences prefs =app.getAppPreferences();
		

		//First of all, make sure we record that an attempt was started.
		Editor editor = prefs.edit();
		editor.putLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, new Date().getTime());
		editor.commit();
		
		app.setupSandbox();
		
		Logger.log(AndroidLogger.TYPE_RESOURCES, "Beginning install attempt for profile " + profileRefs[0]);
		
		
		try {
			
			//This is replicated in the application in a few places.
    		ResourceTable global = platform.getGlobalResourceTable();
    		
    		//Ok, should figure out what the state of this bad boy is.
    		Resource profile = global.getResourceWithId("commcare-application-profile");
    		
    		boolean sanityTest1 = (profile != null && profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED);
    		
    		
			if(upgradeMode){
				
				if(!sanityTest1) return ResourceEngineOutcomes.StatusFailState;
				global.setStateListener(this);
				
				/* temporary is the upgrade table, which starts out in the state that it was left 
				 * after the last install- partially populated if it stopped in middle, empty
				 * if the install was successful
				 */
				ResourceTable temporary = platform.getUpgradeResourceTable();
				ResourceTable recovery = platform.getRecoveryTable();
				temporary.setStateListener(this);

				/*this populates the upgrade table with resources based on binary files,
				 * starting with the profile file. If the new profile is not a newer version,
				 * statgeUpgradeTable doesn't actually pull in all the new references
				 */
				platform.stageUpgradeTable(global, temporary, profileRef, startOverUpgrade);
	    		Resource newProfile = temporary.getResourceWithId("commcare-application-profile");
	    		if(!newProfile.isNewer(profile)) {
	    			Logger.log(AndroidLogger.TYPE_RESOURCES, "App Resources up to Date");
	    			return ResourceEngineOutcomes.StatusUpToDate;
	    		}

				phase = PHASE_CHECKING;
				//Replaces global table with temporary, or w/ recovery if something goes wrong
				platform.upgrade(global, temporary, recovery);
				
				//And see where we ended up to see whether an upgrade actually occurred				
			} else if(partialMode){
				
				global.setStateListener(this);
				platform.init(profileRef, global, false);
				app.writeInstalled();
				
			} else { 
				//this is a standard, clean install
				if(sanityTest1) return ResourceEngineOutcomes.StatusFailState;
				global.setStateListener(this);
				platform.init(profileRef, global, false);
				
				app.writeInstalled();
				
			}
    		
			//Initialize them now that they're installed
			CommCareApplication._().initializeGlobalResources(app);
    		
    		//Alll goood, we need to set our current profile ref to either the one
    		//just used, or the auth ref, if one is available.
    		
    		String authRef = platform.getCurrentProfile().getAuthReference() == null ? profileRef : platform.getCurrentProfile().getAuthReference();
    		
    		prefs = app.getAppPreferences();
    		Editor edit = prefs.edit();
    		edit.putString("default_app_server", authRef);
    		edit.commit();
    		
    		return ResourceEngineOutcomes.StatusInstalled;
		} catch (LocalStorageUnavailableException e) {
			e.printStackTrace();
			
			tryToClearApp();
			
			Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Couldn't install file to local storage|" + e.getMessage());
			return ResourceEngineOutcomes.StatusNoLocalStorage;
		}catch (UnfullfilledRequirementsException e) {
			e.printStackTrace();
			badReqCode = e.getRequirementCode();
			
			vAvailable = e.getAvailableVesionString();
			vRequired= e.getRequiredVersionString();
			majorIsProblem = e.getRequirementCode() == UnfullfilledRequirementsException.REQUIREMENT_MAJOR_APP_VERSION;
			
			tryToClearApp();
			
			Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "App resources are incompatible with this device|" + e.getMessage());
			return ResourceEngineOutcomes.StatusBadReqs;
		} catch (UnresolvedResourceException e) {
			//couldn't find a resource, which isn't good. 
			e.printStackTrace();
			
			tryToClearApp();
			
			Throwable mExceptionCause = e.getCause();
			
			if(mExceptionCause instanceof SSLHandshakeException){		
				Throwable mSecondExceptionCause = mExceptionCause.getCause();
				if(mSecondExceptionCause instanceof CertificateException){
					return ResourceEngineOutcomes.StatusBadCertificate;
				}
			}
			
			missingResourceException = e;
			Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "A resource couldn't be found, almost certainly due to the network|" + e.getMessage());
			if(e.isMessageUseful()) {
				return ResourceEngineOutcomes.StatusMissingDetails;
			} else {
				return ResourceEngineOutcomes.StatusMissing;
			}
		} catch(Exception e) {
			e.printStackTrace();
			
			tryToClearApp();
			
			Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Unknown error ocurred during install|" + e.getMessage());
			return ResourceEngineOutcomes.StatusFailUnknown;
		}
	}
	
	/**
	 *  For now, never clear automatically - just let user choose when to retry vs. resume
	 */
	protected void tryToClearApp(){
		//if(partialMode == false && upgradeMode == false){}
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
	 */
	@Override
	protected void onProgressUpdate(int[]... values) {
		super.onProgressUpdate(values);
	}
		
	@Override
	protected void onPostExecute(ResourceEngineOutcomes result) {
		super.onPostExecute(result);
		//remove all references 
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
