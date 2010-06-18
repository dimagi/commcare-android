/**
 * 
 */
package org.commcare.android.resource.installers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xform.parse.XFormParser;

/**
 * @author ctsims
 *
 */
public class XFormAndroidInstaller extends FileSystemInstaller {

	String namespace;
	
	public XFormAndroidInstaller() {
		
	}
	
	public XFormAndroidInstaller(String localDestination) {
		super(localDestination);
	}
	

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#initialize(org.commcare.util.CommCareInstance)
	 */
	public boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException {
		instance.registerXmlns(namespace, localLocation);
		return true;
	}
	
	protected int customInstall(Reference local) throws IOException {
		FormDef formDef = XFormParser.getFormDef(new InputStreamReader(local.getStream(), "UTF-8"));
		this.namespace = formDef.getInstance().schema;
		return Resource.RESOURCE_STATUS_INSTALLED;
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
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	public void writeExternal(DataOutputStream out) throws IOException {
		super.writeExternal(out);
		ExtUtil.writeString(out, ExtUtil.emptyIfNull(namespace));
	}


}
