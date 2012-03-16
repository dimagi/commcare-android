/**
 * 
 */
package org.commcare.android.resource.installers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.odk.provider.FormsProviderAPI;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xform.parse.XFormParser;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.RemoteException;

/**
 * @author ctsims
 *
 */
public class XFormAndroidInstaller extends FileSystemInstaller {

	String namespace;
	
	String contentUri;
	
	public XFormAndroidInstaller() {
		
	}
	
	public XFormAndroidInstaller(String localDestination, String upgradeDestination) {
		super(localDestination, upgradeDestination);
	}
	

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#initialize(org.commcare.util.CommCareInstance)
	 */
	public boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException {
		instance.registerXmlns(namespace, contentUri);
		return true;
	}
	
	protected int customInstall(Reference local, boolean upgrade) throws IOException {
		FormDef formDef = new XFormParser(new InputStreamReader(local.getStream(), "UTF-8")).parse();
		this.namespace = formDef.getInstance().schema;
		
		
		//TODO: Where should this context be?
		ContentResolver cr = CommCareApplication._().getContentResolver();
		ContentProviderClient cpc = cr.acquireContentProviderClient(FormsProviderAPI.FormsColumns.CONTENT_URI);
		
		ContentValues cv = new ContentValues();
		cv.put(FormsProviderAPI.FormsColumns.DISPLAY_NAME, "NAME");
		cv.put(FormsProviderAPI.FormsColumns.DESCRIPTION, "NAME"); //nullable
		cv.put(FormsProviderAPI.FormsColumns.JR_FORM_ID, formDef.getMainInstance().schema); // ? 
		cv.put(FormsProviderAPI.FormsColumns.FORM_FILE_PATH, local.getLocalURI()); 
		cv.put(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH, GlobalConstants.MEDIA_REF);
		//cv.put(FormsProviderAPI.FormsColumns.SUBMISSION_URI, "NAME"); //nullable
		//cv.put(FormsProviderAPI.FormsColumns.BASE64_RSA_PUBLIC_KEY, "NAME"); //nullable
		
		
		try {
			Uri result = cpc.insert(FormsProviderAPI.FormsColumns.CONTENT_URI, cv);
			this.contentUri = result.toString();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED;
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.resource.installers.FileSystemInstaller#upgrade(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceTable)
	 */
	@Override
	public boolean upgrade(Resource r, ResourceTable table) throws UnresolvedResourceException {
		boolean fileUpgrade = super.upgrade(r, table);
		if(!fileUpgrade) { return false;}
		
		String localRawUri;
		try {
			localRawUri = ReferenceManager._().DeriveReference(this.localLocation).getLocalURI();
		} catch (InvalidReferenceException e) {
			throw new UnresolvedResourceException(r, "Installed resource wasn't able to be derived from " + localLocation);
		}
		
		//We're maintaining this whole Content setup now, so we've goota update things when we move them.
		ContentResolver cr = CommCareApplication._().getContentResolver();
		
		ContentValues cv = new ContentValues();
		cv.put(FormsProviderAPI.FormsColumns.FORM_FILE_PATH, localRawUri); 

		int updatedRows = cr.update(Uri.parse(this.contentUri), cv, null, null);
		if(updatedRows > 1) {
			throw new RuntimeException("Bad URI stored for xforms installer: " + this.contentUri);
		} if(updatedRows == 0) {
			return false;
		}
		
		return true;
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
		this.namespace = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
		this.contentUri = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	public void writeExternal(DataOutputStream out) throws IOException {
		super.writeExternal(out);
		ExtUtil.writeString(out, ExtUtil.emptyIfNull(namespace));
		ExtUtil.writeString(out, ExtUtil.emptyIfNull(contentUri));
	}


}
