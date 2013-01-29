/**
 * 
 */
package org.commcare.android.resource.installers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.DummyResourceTable;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Suite;
import org.commcare.xml.SuiteParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageManager;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.xmlpull.v1.XmlPullParserException;

/**
 * @author ctsims
 *
 */
public class SuiteAndroidInstaller extends FileSystemInstaller {
	
	public SuiteAndroidInstaller() {
		
	}
	
	public SuiteAndroidInstaller(String localDestination, String upgradeDestination) {
		super(localDestination, upgradeDestination);
	}
	

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#initialize(org.commcare.util.CommCareInstance)
	 */
	public boolean initialize(final AndroidCommCarePlatform instance) throws ResourceInitializationException {
		
		try {
			if(localLocation == null) {
				throw new ResourceInitializationException("The suite file's location is null!");
			}
			Reference local = ReferenceManager._().DeriveReference(localLocation);
	
			SuiteParser parser = new SuiteParser(local.getStream(), instance.getGlobalResourceTable(),null) {
				@Override
				protected IStorageUtilityIndexed<FormInstance> getFixtureStorage() {
					return instance.getFixtureStorage();
				}
			};
			
			Suite s = parser.parse();
			
			instance.registerSuite(s);
			
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
	
	public boolean install(Resource r, ResourceLocation location, Reference ref, ResourceTable table, final AndroidCommCarePlatform instance, boolean upgrade) throws UnresolvedResourceException, UnfullfilledRequirementsException{
		//First, make sure all the file stuff is managed.
		super.install(r, location, ref, table, instance, upgrade);
		System.out.println("143 suite android install");
		try {
			Reference local = ReferenceManager._().DeriveReference(localLocation);
			
			SuiteParser parser = new SuiteParser(local.getStream(), table, r.getRecordGuid()) {
				@Override
				protected IStorageUtilityIndexed<FormInstance> getFixtureStorage() {
					return instance.getFixtureStorage();
				}
			};
			
			Suite s = parser.parse();
			
			table.commit(r, upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED);
			return true;
		} catch (InvalidReferenceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidStructureException e) {
			e.printStackTrace();
			throw new UnresolvedResourceException(r, e.getMessage(), true);
		} catch (XmlPullParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return false;
	}
	
	protected int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException {
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
	
	public boolean verifyInstallation(Resource r, Vector<UnresolvedResourceException> problems) {

		try{
			Reference local = ReferenceManager._().DeriveReference(localLocation);
			Suite mSuite = (new SuiteParser(local.getStream(), new DummyResourceTable(), null) {
				@Override
				protected IStorageUtilityIndexed<FormInstance> getFixtureStorage() {
					//shouldn't be necessary
					return null;
				}
			}).parse();
			Hashtable<String,Entry> mHashtable = mSuite.getEntries();
			for(Enumeration en = mHashtable.keys();en.hasMoreElements() ; ){
				String key = (String)en.nextElement();
			}
			Vector<Menu> menus = mSuite.getMenus();
			Enumeration e = menus.elements();
			while(e.hasMoreElements()){
				Menu mMenu = (Menu)e.nextElement();
				String aURI = mMenu.getAudioURI();
				String iURI = mMenu.getImageURI();
				Reference aRef = ReferenceManager._().DeriveReference(aURI);
				Reference iRef = ReferenceManager._().DeriveReference(iURI);
				
				if(!aRef.doesBinaryExist()){
					String audioLocalReference = aRef.getLocalURI();
					problems.addElement(new UnresolvedResourceException(r,"Missing external media: " + audioLocalReference));
				}
				if(!iRef.doesBinaryExist()){
					String imageLocalReference = iRef.getLocalURI();
					problems.addElement(new UnresolvedResourceException(r,"Missing external media: " + imageLocalReference));
				}
			}
		}
		catch(Exception e){
			System.out.println("fail");
		}

		if(problems.size() == 0 ) { return false;}
		return true;
	}


}
