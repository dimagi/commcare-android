/**
 * 
 */
package org.commcare.android.util;

import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.resource.installers.LocaleAndroidInstaller;
import org.commcare.android.resource.installers.ProfileAndroidInstaller;
import org.commcare.android.resource.installers.SuiteAndroidInstaller;
import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.resources.model.InstallerFactory;
import org.commcare.resources.model.ResourceInstaller;

/**
 * @author ctsims
 *
 */
public class AndroidResourceInstallerFactory extends InstallerFactory {

	public ResourceInstaller getXFormInstaller() {
		return new XFormAndroidInstaller(GlobalConstants.INSTALL_REF);
	}
	
	public ResourceInstaller getProfileInstaller(boolean forceInstall) {
		return new ProfileAndroidInstaller(GlobalConstants.INSTALL_REF);
	}

	public ResourceInstaller getSuiteInstaller() {
		return new SuiteAndroidInstaller(GlobalConstants.INSTALL_REF);
	}
	
	public ResourceInstaller getLocaleFileInstaller(String locale) {
		return new LocaleAndroidInstaller(GlobalConstants.INSTALL_REF, locale);
	}
}
