/**
 * 
 */
package org.commcare.android.resource.installers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceInstaller;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.util.StreamUtil;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public abstract class FileSystemInstaller implements ResourceInstaller<AndroidCommCarePlatform> {
	String localLocation;
	String localDestination;
	
	public FileSystemInstaller() {
		
	}
	
	public FileSystemInstaller(String localDestination) {
		this.localDestination = localDestination;
	}
	
	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#cleanup()
	 */
	public void cleanup() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#initialize(org.commcare.util.CommCareInstance)
	 */
	public abstract boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException;

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#install(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceLocation, org.javarosa.core.reference.Reference, org.commcare.resources.model.ResourceTable, org.commcare.util.CommCareInstance, boolean)
	 */
	public boolean install(Resource r, ResourceLocation location, Reference ref, ResourceTable table, AndroidCommCarePlatform instance, boolean upgrade) throws UnresolvedResourceException, UnfullfilledRequirementsException {
		localLocation = localDestination + "/" + r.getResourceId() + ".xml";
		if(upgrade) {
			//Move to temp, move, then move back.
		} else {
				try {
				//Stream to location
				Reference local = ReferenceManager._().DeriveReference(localLocation);
				StreamUtil.transfer(ref.getStream(), local.getOutputStream());
				
				int status = customInstall(local);
				
				table.commit(r, status);
				return true;
			} catch (InvalidReferenceException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return false;
	}

	protected abstract int customInstall(Reference local) throws IOException;
	
	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#requiresRuntimeInitialization()
	 */
	public abstract boolean requiresRuntimeInitialization();

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#uninstall(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceTable, org.commcare.resources.model.ResourceTable)
	 */
	public boolean uninstall(Resource r, ResourceTable table, ResourceTable incoming) throws UnresolvedResourceException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#upgrade(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceTable)
	 */
	public boolean upgrade(Resource r, ResourceTable table) throws UnresolvedResourceException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
	 */
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		this.localLocation = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
		this.localDestination = ExtUtil.readString(in);
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeString(out, ExtUtil.emptyIfNull(localLocation));
		ExtUtil.writeString(out, localDestination);
	}
}
