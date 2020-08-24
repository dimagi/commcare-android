package org.commcare.android.resource.installers;

import org.commcare.resources.model.Resource;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.DataInputStream;
import java.io.IOException;

// This class was used prior to Commcare v2.42 and should currently only be used for migration purposes
public class XFormAndroidInstallerV8 extends FileSystemInstaller {

    private String namespace;
    private String contentUri;

    @SuppressWarnings("unused")
    public XFormAndroidInstallerV8() {
        // for externalization
    }

    @Override
    public boolean initialize(AndroidCommCarePlatform platform, boolean isUpgrade) throws
            IOException, InvalidReferenceException, InvalidStructureException,
            XmlPullParserException, UnfullfilledRequirementsException {
        return false;
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade, AndroidCommCarePlatform platform) throws IOException, UnresolvedResourceException {
        return 0;
    }

    @Override
    public boolean requiresRuntimeInitialization() {
        return false;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);
        this.namespace = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        this.contentUri = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
    }

    public String getContentUri() {
        return contentUri;
    }

    public String getNamespace() {
        return namespace;
    }
}
