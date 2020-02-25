package org.commcare.android.resource.installers;

import org.commcare.resources.model.Resource;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class XFormUpdateInfoInstaller extends XFormAndroidInstaller {

    @SuppressWarnings("unused")
    public XFormUpdateInfoInstaller() {
        // for externalization
    }

    public XFormUpdateInfoInstaller(String localDestination, String upgradeDestination) {
        super(localDestination, upgradeDestination);
    }

    @Override
    public boolean initialize(Resource r, AndroidCommCarePlatform platform, boolean isUpgrade) throws
            IOException, InvalidReferenceException, InvalidStructureException,
            XmlPullParserException, UnfullfilledRequirementsException {
        platform.setUpdateInfoFormXmlns(namespace);
        return super.initialize(r, platform, isUpgrade);
    }
}
