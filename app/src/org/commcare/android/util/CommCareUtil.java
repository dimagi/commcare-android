/**
 * 
 */
package org.commcare.android.util;

import java.util.Vector;

import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.ArrayUtilities;
import org.javarosa.xpath.expr.XPathExpression;

/**
 * Basically Copy+Paste code from CCJ2ME that needs to be unified or re-indexed to somewhere more reasonable.
 * 
 * @author ctsims
 *
 */
public class CommCareUtil {

	public static FormInstance loadFixture(String refId, String userId) {
		IStorageUtilityIndexed storage = CommCareApplication._().getStorage("fixture", FormInstance.class);
		
		Vector<Integer> relevantFixtures = storage.getIDsForValue(FormInstance.META_ID, refId);
		
		///... Nooooot so clean.
		if(relevantFixtures.size() == 1) {
			//easy case, one fixture, use it
			return (FormInstance)storage.read(relevantFixtures.elementAt(0).intValue());
			//TODO: Userid check anyway?
		} else if(relevantFixtures.size() > 1){
			//intersect userid and fixtureid set.
			//TODO: Replace context call here with something from the session, need to stop relying on that coupling
			
			Vector<Integer> relevantUserFixtures = storage.getIDsForValue(FormInstance.META_XMLNS, userId);
			
			if(relevantUserFixtures.size() != 0) {
				Integer userFixture = ArrayUtilities.intersectSingle(relevantFixtures, relevantUserFixtures);
				if(userFixture != null) {
					return (FormInstance)storage.read(userFixture.intValue());
				}
			}
			//Oooookay, so there aren't any fixtures for this user, see if there's a global fixture.				
			Integer globalFixture = ArrayUtilities.intersectSingle(storage.getIDsForValue(FormInstance.META_XMLNS, ""), relevantFixtures);
			if(globalFixture == null) {
				//No fixtures?! What is this. Fail somehow. This method should really have an exception contract.
				return null;
			}
			return (FormInstance)storage.read(globalFixture.intValue());
		} else {
			return null;
		}
	}

	/**
	 * Used around to count up the degree of specificity for this reference
	 * 
	 * @param reference
	 * @return
	 */
	public static int countPreds(TreeReference reference) {
		int preds = 0;
		for(int i =0 ; i < reference.size(); ++i) {
			Vector<XPathExpression> predicates = reference.getPredicate(i);
			if(predicates != null) {
				preds += predicates.size();
			}
		}
		return preds;
	}
}
