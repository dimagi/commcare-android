/**
 * 
 */
package org.commcare.android.util;

import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.resource.installers.LocaleAndroidInstaller;
import org.commcare.android.resource.installers.MediaFileAndroidInstaller;
import org.commcare.android.resource.installers.ProfileAndroidInstaller;
import org.commcare.android.resource.installers.SuiteAndroidInstaller;
import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.resources.model.InstallerFactory;
import org.commcare.resources.model.ResourceInstaller;

/**
 * @author ctsims
 *
 */
public class AndroidResourceInstallerFactory extends InstallerFactory {

    CommCareApp app;
    
    public AndroidResourceInstallerFactory(CommCareApp app ) {
        this.app = app;
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.InstallerFactory#getXFormInstaller()
     */
    @Override
    public ResourceInstaller getXFormInstaller() {
        return new XFormAndroidInstaller(GlobalConstants.INSTALL_REF, GlobalConstants.UPGRADE_REF);
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.InstallerFactory#getProfileInstaller(boolean)
     */
    @Override
    public ResourceInstaller getProfileInstaller(boolean forceInstall) {
        return new ProfileAndroidInstaller(GlobalConstants.INSTALL_REF, GlobalConstants.UPGRADE_REF);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.InstallerFactory#getSuiteInstaller()
     */
    @Override
    public ResourceInstaller getSuiteInstaller() {
        return new SuiteAndroidInstaller(GlobalConstants.INSTALL_REF, GlobalConstants.UPGRADE_REF);
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.InstallerFactory#getLocaleFileInstaller(java.lang.String)
     */
    @Override
    public ResourceInstaller getLocaleFileInstaller(String locale) {
        return new LocaleAndroidInstaller(GlobalConstants.INSTALL_REF, GlobalConstants.UPGRADE_REF, locale);
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.InstallerFactory#getMediaInstaller(java.lang.String)
     */
    @Override
    public ResourceInstaller getMediaInstaller(String path) {
        return new MediaFileAndroidInstaller(GlobalConstants.MEDIA_REF, GlobalConstants.UPGRADE_REF, path);
    }
}
