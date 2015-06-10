/**
 * 
 */
package org.commcare.android.util;

import org.commcare.android.cases.AndroidCaseInstanceTreeElement;
import org.commcare.android.cases.AndroidLedgerInstanceTreeElement;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.User;
import org.commcare.cases.instance.CaseInstanceTreeElement;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.ledger.instance.LedgerInstanceTreeElement;
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
    AndroidCaseInstanceTreeElement casebase;
    LedgerInstanceTreeElement stockbase;
    
    public CommCareInstanceInitializer(){ 
        this(null);
    }
    public CommCareInstanceInitializer(CommCareSession session) {
        this.session = session;
    }
    
    public AbstractTreeElement generateRoot(ExternalDataInstance instance) {
        CommCareApplication app = CommCareApplication._();
        String ref = instance.getReference();
        if(ref.indexOf(LedgerInstanceTreeElement.MODEL_NAME) != -1) {
            if(stockbase == null) {
                SqlStorage<Ledger> storage = app.getUserStorage(Ledger.STORAGE_KEY, Ledger.class);
                stockbase =  new AndroidLedgerInstanceTreeElement(instance.getBase(), storage);
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
            instance.setCacheHost(casebase);
            return casebase;
        }else if(instance.getReference().indexOf("fixture") != -1) {
            //TODO: This is all just copied from J2ME code. that's pretty silly. unify that.
            String userId = "";
            User u;
            try {
                u = CommCareApplication._().getSession().getLoggedInUser();
            } catch (SessionUnavailableException e) {
                throw new UserStorageClosedException(e.getMessage());
            }

            if(u != null) {
                userId = u.getUniqueId();
            }
            
            String refId = ref.substring(ref.lastIndexOf('/') + 1, ref.length());
            try{
                FormInstance fixture = CommCareUtil.loadFixture(refId, userId);
                
                if(fixture == null) {
                    throw new RuntimeException("Could not find an appropriate fixture for src: " + ref);
                }
                
                TreeElement root = fixture.getRoot();
                root.setParent(instance.getBase());
                return root;
                
            } catch(IllegalStateException ise){
                throw new RuntimeException("Could not load fixture for src: " + ref);
            }
        }
        if(instance.getReference().indexOf("session") != -1) {
            User u;
            try {
                u = CommCareApplication._().getSession().getLoggedInUser();
            } catch (SessionUnavailableException e) {
                throw new UserStorageClosedException(e.getMessage());
            }
            TreeElement root = session.getSessionInstance(app.getPhoneId(), app.getCurrentVersionString(), u.getUsername(), u.getUniqueId(), u.getProperties()).getRoot();
            root.setParent(instance.getBase());
            return root;
        }
        return null;
    }
}
