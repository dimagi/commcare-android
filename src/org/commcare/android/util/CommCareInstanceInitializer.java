/**
 * 
 */
package org.commcare.android.util;

import java.util.Vector;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.ACase;
import org.commcare.android.models.User;
import org.commcare.cases.instance.CaseInstanceTreeElement;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.ArrayUtilities;

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
				casebase =  new CaseInstanceTreeElement(instance.getBase(), app.getStorage(ACase.STORAGE_KEY, ACase.class), false);
			} else {
				casebase.rebase(instance.getBase());
			}
			return casebase;
		}
		if(instance.getReference().indexOf("fixture") != -1) {
			
			//TODO: This is all just copied from J2ME code. that's pretty silly. unify that.
			
			String userId = "";
			User u = CommCareApplication._().getSession().getLoggedInUser();
			if(u != null) {
				userId = u.getUniqueId();
			}
			
			String refId = ref.substring(ref.lastIndexOf('/') + 1, ref.length());
			
			IStorageUtilityIndexed storage = CommCareApplication._().getStorage("fixture", FormInstance.class);
			
			FormInstance fixture = null;
			Vector<Integer> relevantFixtures = storage.getIDsForValue(FormInstance.META_ID, refId);
			
			///... Nooooot so clean.
			if(relevantFixtures.size() == 1) {
				//easy case, one fixture, use it
				fixture = (FormInstance)storage.read(relevantFixtures.elementAt(0).intValue());
				//TODO: Userid check anyway?
			} else if(relevantFixtures.size() > 1){
				//intersect userid and fixtureid set.
				//TODO: Replace context call here with something from the session, need to stop relying on that coupling
				
				Vector<Integer> relevantUserFixtures = storage.getIDsForValue(FormInstance.META_XMLNS, userId);
				
				if(relevantUserFixtures.size() != 0) {
					Integer userFixture = ArrayUtilities.intersectSingle(relevantFixtures, relevantUserFixtures);
					if(userFixture != null) {
						fixture = (FormInstance)storage.read(userFixture.intValue());
					}
				}
				if(fixture == null) {
					//Oooookay, so there aren't any fixtures for this user, see if there's a global fixture.				
					Integer globalFixture = ArrayUtilities.intersectSingle(storage.getIDsForValue(FormInstance.META_XMLNS, ""), relevantFixtures);
					if(globalFixture == null) {
						//No fixtures?! What is this. Fail somehow. This method should really have an exception contract.
						return null;
					}
					fixture = (FormInstance)storage.read(globalFixture.intValue());
				}
			} else {
				fixture = null;
			}
			
			if(fixture == null) {
				throw new RuntimeException("Could not find an appropriate fixture for src: " + ref);
			}
			
			TreeElement root = fixture.getRoot();
			root.setParent(instance.getBase());
			return root;
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
