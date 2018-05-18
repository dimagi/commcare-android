package org.commcare.android.resource.installers;

import org.commcare.utils.AndroidCommCarePlatform;

public class XFormUpdateInfoInstaller extends XFormAndroidInstaller {

    @SuppressWarnings("unused")
    public XFormUpdateInfoInstaller() {
        // for externalization
    }

    public XFormUpdateInfoInstaller(String localDestination, String upgradeDestination) {
        super(localDestination, upgradeDestination);
    }

    @Override
    public boolean initialize(AndroidCommCarePlatform platform, boolean isUpgrade) {
        platform.setUpdateInfoFormXmlns(namespace);
        return super.initialize(platform, isUpgrade);
    }
}
