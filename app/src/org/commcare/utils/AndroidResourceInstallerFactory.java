package org.commcare.utils;

import org.commcare.engine.resource.installers.LocaleAndroidInstaller;
import org.commcare.engine.resource.installers.MediaFileAndroidInstaller;
import org.commcare.engine.resource.installers.ProfileAndroidInstaller;
import org.commcare.engine.resource.installers.SuiteAndroidInstaller;
import org.commcare.engine.resource.installers.XFormAndroidInstaller;
import org.commcare.resources.model.InstallerFactory;
import org.commcare.resources.model.ResourceInstaller;

/**
 * @author ctsims
 */
public class AndroidResourceInstallerFactory extends InstallerFactory {

    public AndroidResourceInstallerFactory() {
    }

    @Override
    public ResourceInstaller getXFormInstaller() {
        return new XFormAndroidInstaller(GlobalConstants.INSTALL_REF, GlobalConstants.UPGRADE_REF);
    }

    @Override
    public ResourceInstaller getProfileInstaller(boolean forceInstall) {
        return new ProfileAndroidInstaller(GlobalConstants.INSTALL_REF, GlobalConstants.UPGRADE_REF);
    }

    @Override
    public ResourceInstaller getSuiteInstaller() {
        return new SuiteAndroidInstaller(GlobalConstants.INSTALL_REF, GlobalConstants.UPGRADE_REF);
    }

    @Override
    public ResourceInstaller getLocaleFileInstaller(String locale) {
        return new LocaleAndroidInstaller(GlobalConstants.INSTALL_REF, GlobalConstants.UPGRADE_REF, locale);
    }

    @Override
    public ResourceInstaller getMediaInstaller(String path) {
        return new MediaFileAndroidInstaller(GlobalConstants.MEDIA_REF, GlobalConstants.UPGRADE_REF, path);
    }
}
