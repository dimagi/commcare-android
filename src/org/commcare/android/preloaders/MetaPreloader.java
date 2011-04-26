/**
 * 
 */
package org.commcare.android.preloaders;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.User;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.utils.IPreloadHandler;

/**
 * @author ctsims
 *
 */
public class MetaPreloader implements IPreloadHandler {
	
	AndroidCommCarePlatform platform;
	
	public MetaPreloader(AndroidCommCarePlatform platform) {
		this.platform = platform;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.model.utils.IPreloadHandler#handlePostProcess(org.javarosa.core.model.instance.TreeElement, java.lang.String)
	 */
	public boolean handlePostProcess(TreeElement node, String params) {
		//Nothing yet!
		return false;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.model.utils.IPreloadHandler#handlePreload(java.lang.String)
	 */
	public IAnswerData handlePreload(String preloadParams) {
		User u = CommCareApplication._().getSession().getLoggedInUser();
		if(preloadParams == null ) { return null; }
		 if("UserName".toLowerCase().equals(preloadParams.toLowerCase())) {
			 if(u == null) { return null; }
			return new UncastData(u.getUsername());
		 } else if("UserID".toLowerCase().equals(preloadParams.toLowerCase())) {
			if(u == null) { return null; }
			return new UncastData(u.getUniqueId());
	 	 } else if("callduration".toLowerCase().equals(preloadParams.toLowerCase())) { 
	 		 long duration = platform.getCallDuration();
	 		 if(duration ==0 ) { return null; }
	 		 return new UncastData(String.valueOf((int)Math.ceil(duration / 1000.0 / 60.0)));
	 	 } else {
	 		 return null;
	 	 }
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.model.utils.IPreloadHandler#preloadHandled()
	 */
	public String preloadHandled() {
		return "meta";
	}

}
