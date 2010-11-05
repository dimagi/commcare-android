/**
 * 
 */
package org.commcare.android.preloaders;

import org.commcare.android.models.Case;
import org.commcare.android.models.Referral;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.utils.IPreloadHandler;

/**
 * @author ctsims
 *
 */
public class ReferralPreloader implements IPreloadHandler {
	
	Case c;
	
	private final Referral referral;
	
	/**
	 * Creates a preloader using the provided Referral.
	 * @param referral The object that should be used to preload data.
	 */
	public ReferralPreloader(Referral referral) {
		this.referral = referral;
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.model.utils.IPreloadHandler#handlePostProcess(org.javarosa.core.model.instance.TreeElement, java.lang.String)
	 */
	public boolean handlePostProcess(TreeElement node, String params) {
		// No post-processing supported as of this time.
		return false;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.model.utils.IPreloadHandler#handlePreload(java.lang.String)
	 */
	public IAnswerData handlePreload(String preloadParams) {
		if(preloadParams.equals("id")) {
			return new StringData(referral.getReferralId());
		} if(preloadParams.equals("type")) {
			return new StringData(referral.getType());
		} if(preloadParams.equals("date-due")) {
			return new DateData(referral.getDateDue());
		} if(preloadParams.equals("date-created")) {
			return new DateData(referral.getDateCreated());
		} else {
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.model.utils.IPreloadHandler#preloadHandled()
	 */
	public String preloadHandled() {
		return "patient_referral";
	}

}
