/**
 * 
 */
package org.commcare.android.preloaders;

import org.commcare.android.models.Case;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.utils.IPreloadHandler;
import org.javarosa.core.model.utils.PreloadUtils;

/**
 * @author ctsims
 *
 */
public class CasePreloader implements IPreloadHandler {
	
	Case c;
	
	public CasePreloader(Case c) {
		this.c = c;
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
		 if("name".equals(preloadParams)) {
			return new StringData(c.getName());
		 } else if("external-id".equals(preloadParams)) {
			return new StringData(c.getExternalId());
	 	 } else if("case-id".equals(preloadParams)) {
	 		return new UncastData(c.getCaseId());
	 	 } else if("status".equals(preloadParams)) {
		 	if(c.isClosed()) {
				return new StringData("closed");
			} else {
				return new StringData("open");
			}
		} else if("date-opened".equals(preloadParams)) {
			return new DateData(c.getDateOpened());
		} else {
			Object retVal = c.getProperty(preloadParams);
			if(retVal instanceof String) {
				return new UncastData((String)retVal);
			}
			return PreloadUtils.wrapIndeterminedObject(retVal);
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.model.utils.IPreloadHandler#preloadHandled()
	 */
	public String preloadHandled() {
		return "case";
	}

}
