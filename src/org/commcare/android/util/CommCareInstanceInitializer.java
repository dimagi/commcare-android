/**
 * 
 */
package org.commcare.android.util;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.ACase;
import org.commcare.android.models.User;
import org.commcare.cases.instance.CaseInstanceTreeElement;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeElement;

/**
 * @author ctsims
 *
 */
public class CommCareInstanceInitializer extends InstanceInitializationFactory {
	AndroidCommCarePlatform platform;
	CaseInstanceTreeElement casebase;
	
	public CommCareInstanceInitializer(){ 
		this(null);
	}
	public CommCareInstanceInitializer(AndroidCommCarePlatform platform) {
		this.platform = platform;
	}
	
	public AbstractTreeElement generateRoot(ExternalDataInstance instance) {
		CommCareApplication app = CommCareApplication._();
		String ref = instance.getReference();
		if(ref.indexOf("case") != -1) {
			if(casebase == null) {
				casebase =  new CaseInstanceTreeElement(instance.getBase(), app.getStorage(ACase.STORAGE_KEY, ACase.class));
			} else {
				casebase.rebase(instance.getBase());
			}
			return casebase;
		}
		if(instance.getReference().indexOf("fixture") != -1) {
			throw new RuntimeException("Fixtures unimplemented!");
		}
		if(instance.getReference().indexOf("session") != -1) {
			User u = app.getSession().getLoggedInUser();
			TreeElement root = platform.getSession().getSessionInstance(app.getPhoneId(), app.getCurrentVersionString(), u.getUsername(), u.getUniqueId()).getRoot();
			root.setParent(instance.getBase());
			return root;
		}
		return null;
	}
}
