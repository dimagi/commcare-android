/**
 * 
 */
package org.commcare.android.util;

import java.util.HashSet;
import java.util.Vector;

import org.commcare.android.cases.AndroidCaseInstanceTreeElement;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.User;
import org.commcare.cases.instance.CaseInstanceTreeElement;
import org.commcare.cases.stock.Stock;
import org.commcare.cases.stock.instance.StockInstanceTreeElement;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCareSession;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeElement;

/**
 * @author ctsims
 *
 */
public class CommCareInstanceInitializer extends InstanceInitializationFactory {
	CommCareSession session;
	CaseInstanceTreeElement casebase;
	StockInstanceTreeElement stockbase;
	
	public CommCareInstanceInitializer(){ 
		this(null);
	}
	public CommCareInstanceInitializer(CommCareSession session) {
		this.session = session;
	}
	
	public AbstractTreeElement generateRoot(ExternalDataInstance instance) {
		CommCareApplication app = CommCareApplication._();
		String ref = instance.getReference();
		if(ref.indexOf(StockInstanceTreeElement.MODEL_NAME) != -1) {
			if(stockbase == null) {
				SqlStorage<Stock> storage = app.getUserStorage(Stock.STORAGE_KEY, Stock.class);
				stockbase =  new StockInstanceTreeElement(instance.getBase(), storage);
			} else {
				//re-use the existing model if it exists.
				stockbase.rebase(instance.getBase());
			}
			return stockbase;
		}else if(ref.indexOf(CaseInstanceTreeElement.MODEL_NAME) != -1) {
			if(casebase == null) {
				SqlStorage<ACase> storage = app.getUserStorage(ACase.STORAGE_KEY, ACase.class);
				casebase =  new AndroidCaseInstanceTreeElement(instance.getBase(), storage, false);
			} else {
				//re-use the existing model if it exists.
				casebase.rebase(instance.getBase());
			}
			return casebase;
		}else if(instance.getReference().indexOf("fixture") != -1) {
			
			//TODO: This is all just copied from J2ME code. that's pretty silly. unify that.
			
			String userId = "";
			User u = CommCareApplication._().getSession().getLoggedInUser();
			if(u != null) {
				userId = u.getUniqueId();
			}
			
			String refId = ref.substring(ref.lastIndexOf('/') + 1, ref.length());
			
			FormInstance fixture = CommCareUtil.loadFixture(refId, userId);
			
			if(fixture == null) {
				throw new RuntimeException("Could not find an appropriate fixture for src: " + ref);
			}
			
			TreeElement root = fixture.getRoot();
			root.setParent(instance.getBase());
			return root;
		}
		if(instance.getReference().indexOf("session") != -1) {
			User u = app.getSession().getLoggedInUser();
			TreeElement root = session.getSessionInstance(app.getPhoneId(), app.getCurrentVersionString(), u.getUsername(), u.getUniqueId(), u.getProperties()).getRoot();
			root.setParent(instance.getBase());
			return root;
		}
		return null;
	}
}
