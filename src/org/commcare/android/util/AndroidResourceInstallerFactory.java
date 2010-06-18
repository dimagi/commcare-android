/**
 * 
 */
package org.commcare.android.util;

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
		return new XFormAndroidInstaller("jr://file/commcare/");
	}
	
	public ResourceInstaller getProfileInstaller(boolean forceInstall) {
		return new ProfileAndroidInstaller("jr://file/commcare/");
	}

	public ResourceInstaller getSuiteInstaller() {
		return new SuiteAndroidInstaller("jr://file/commcare/");
	}

}
