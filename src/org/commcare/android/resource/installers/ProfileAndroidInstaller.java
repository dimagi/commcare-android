/**
 * 
 */
package org.commcare.android.resource.installers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.PropertySetter;
import org.commcare.xml.ProfileParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.xmlpull.v1.XmlPullParserException;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

/**
 * @author ctsims
 *
 */
public class ProfileAndroidInstaller extends FileSystemInstaller {
	
	public ProfileAndroidInstaller() {
		
	}
	
	public ProfileAndroidInstaller(String localDestination, String upgradeDestination) {
		super(localDestination, upgradeDestination);
	}
	

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#initialize(org.commcare.util.CommCareInstance)
	 */
	public boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException {
		
		try {
		
			Reference local = ReferenceManager._().DeriveReference(localLocation);
			
			
			ProfileParser parser = new ProfileParser(local.getStream(), instance, instance.getGlobalResourceTable(), null, 
					Resource.RESOURCE_STATUS_INSTALLED, false);
			
			Profile p = parser.parse();
			instance.setProfile(p);
			
			return true;
		} catch (InvalidReferenceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidStructureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (XmlPullParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnfullfilledRequirementsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return false;
	}
	
	public boolean install(Resource r, ResourceLocation location, Reference ref, ResourceTable table, AndroidCommCarePlatform instance, boolean upgrade) throws UnresolvedResourceException, UnfullfilledRequirementsException{
		//First, make sure all the file stuff is managed.
		super.install(r, location, ref, table, instance, upgrade);
		
		try {
			Reference local = ReferenceManager._().DeriveReference(localLocation);
	
			
			ProfileParser parser = new ProfileParser(local.getStream(), instance, table, r.getRecordGuid(), 
					upgrade ? Resource.RESOURCE_STATUS_UNINITIALIZED : Resource.RESOURCE_STATUS_UNINITIALIZED, false);
			
			Profile p = parser.parse();
			
			if(!upgrade) {
				initProperties(p);
			}
			
			table.commit(r, upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED, p.getVersion());
			return true;
		} catch (InvalidReferenceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidStructureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (XmlPullParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return false;
	}
	
	private void initProperties(Profile profile) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CommCareApplication._());
		Editor editor = prefs.edit();
		for(PropertySetter p : profile.getPropertySetters()) {
			editor.putString(p.getKey(), p.isForce() ? p.getValue() : prefs.getString(p.getKey(), p.getValue()));
		}
		editor.commit();
	}
	
	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#upgrade(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceTable)
	 */
	public boolean upgrade(Resource r, ResourceTable table) throws UnresolvedResourceException {
		if(!super.upgrade(r, table)) {
			return false;
		}
		//TODO: Figure out how to parse the profile without messing w/the resources and then init 
		//the properties
		return true;
	}
	
	protected int customInstall(Reference local, boolean upgrade) throws IOException {
		return Resource.RESOURCE_STATUS_LOCAL;
	}

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#requiresRuntimeInitialization()
	 */
	public boolean requiresRuntimeInitialization() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
	 */
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		super.readExternal(in, pf);
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	public void writeExternal(DataOutputStream out) throws IOException {
		super.writeExternal(out);
	}


}
