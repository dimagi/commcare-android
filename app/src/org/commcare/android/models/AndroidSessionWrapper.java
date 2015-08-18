package org.commcare.android.models;

import android.content.Context;
import android.util.Log;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidInstanceInitializer;
import org.commcare.android.util.CommCareUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.suite.model.StackOperation;
import org.commcare.suite.model.Suite;
import org.commcare.suite.model.Text;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareSession;
import org.commcare.util.SessionFrame;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xpath.expr.XPathEqExpr;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathStringLiteral;

import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import javax.crypto.SecretKey;

/**
 * This is a container class which maintains all of the appropriate hooks for managing the details
 * of the current "state" of an application (the session, the relevant forms) and the hooks for 
 * manipulating them in a single place.
 * 
 * @author ctsims
 */
public class AndroidSessionWrapper {
    private static final String TAG = AndroidSessionWrapper.class.getSimpleName();
    //The state descriptor will need these 
    protected CommCareSession session;
    private CommCarePlatform platform;
    protected int formRecordId = -1;
    protected int sessionStateRecordId = -1;

    public AndroidSessionWrapper(CommCarePlatform platform) {
        session = new CommCareSession(platform);
        this.platform = platform;
    }
    
    /**
     * Serialize the state of this session so it can be restored 
     * at a later time. 
     */
    public SessionStateDescriptor getSessionStateDescriptor() {
        return new SessionStateDescriptor(this);
    }
    
    public void loadFromStateDescription(SessionStateDescriptor descriptor) {
        this.reset();
        this.sessionStateRecordId = descriptor.getID();
        this.formRecordId = descriptor.getFormRecordId();
        descriptor.loadSession(this.session);
    }
    
    /**
     * Clear all local state and return this session to completely fresh
     */
    public void reset() {
        this.session.clearAllState();
        cleanVolatiles();
    }
    
    /**
     * Clears out all of the elements of this wrapper which are for an individual traversal.
     * Includes any cached info (since the casedb might have changed) and the individual id's
     * and such.
     */
    private void cleanVolatiles() {
        formRecordId = -1;
        sessionStateRecordId = -1;
        //CTS - Added to fix bugs where casedb didn't get renewed between sessions (possibly
        //we want to "update" the casedb rather than rebuild it, but this is safest for now.
        initializer = null;
    }
    
    public CommCareSession getSession() {
        return session;
    }

    /**
     * Lookup the current form record in database using the form record id
     *
     * @return FormRecord or null
     */
    public FormRecord getFormRecord() {
        if (formRecordId == -1) {
            return null;
        }

        SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
        return storage.read(formRecordId);
    }
    
    public void setFormRecordId(int formRecordId) {
        this.formRecordId = formRecordId;
    }
    

    public int getFormRecordId() {
        return formRecordId;
    }

    /**
     * Search for a saved sessions that has an incomplete form record using the
     * same case as the one in the current session descriptor.
     *
     * @return Descriptor of the first saved session that has has an incomplete
     * form record with the same case found in the current descriptor;
     * otherwise null.
     */
    public SessionStateDescriptor getExistingIncompleteCaseDescriptor() {
        SessionStateDescriptor ssd = getSessionStateDescriptor();

        if (!ssd.getSessionDescriptor().contains(SessionFrame.STATE_DATUM_VAL)) {
            // don't continue if the current session doesn't use a case
            return null;
        }

        SqlStorage<FormRecord> storage =
                CommCareApplication._().getUserStorage(FormRecord.class);

        SqlStorage<SessionStateDescriptor> sessionStorage =
                CommCareApplication._().getUserStorage(SessionStateDescriptor.class);

        // TODO: This is really a join situation. Need a way to outline
        // connections between tables to enable joining

        // See if this session's unique hash corresponds to any pending forms.
        Vector<Integer> ids = sessionStorage.getIDsForValue(SessionStateDescriptor.META_DESCRIPTOR_HASH, ssd.getHash());

        // Filter for forms which have actually been started.
        for (int id : ids) {
            try {
                int recordId = Integer.valueOf(sessionStorage.getMetaDataFieldForRecord(id, SessionStateDescriptor.META_FORM_RECORD_ID));
                if (!storage.exists(recordId)) {
                    sessionStorage.remove(id);
                    Log.d(TAG, "Removing stale ssd record: " + id);
                    continue;
                }
                if (FormRecord.STATUS_INCOMPLETE.equals(storage.getMetaDataFieldForRecord(recordId, FormRecord.META_STATUS))) {
                    return sessionStorage.read(id);
                }
            } catch (NumberFormatException nfe) {
                // TODO: Clean up this record
            }
        }
        return null;
    }

    public void commitStub() throws StorageFullException {
        //TODO: This should now be locked somehow
        SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
        SqlStorage<SessionStateDescriptor> sessionStorage = CommCareApplication._().getUserStorage(SessionStateDescriptor.class);

        SecretKey  key;
        try {
            key = CommCareApplication._().createNewSymetricKey();
        } catch (SessionUnavailableException e) {
            // the user db is closed
            throw new UserStorageClosedException(e.getMessage());
        }
        
        //TODO: this has two components which can fail. be able to roll them back
        
        FormRecord r = new FormRecord("", FormRecord.STATUS_UNSTARTED, getSession().getForm(), key.getEncoded(), null, new Date(0));
        storage.write(r);
        setFormRecordId(r.getID());
        
        SessionStateDescriptor ssd = getSessionStateDescriptor();
        sessionStorage.write(ssd);
        sessionStateRecordId = ssd.getID();
    }

    public int getSessionDescriptorId() {
        return sessionStateRecordId;
    }

    public String getHeaderTitle(Context context, AndroidCommCarePlatform platform) {
        String descriptor = context.getString(R.string.application_name);
        Hashtable<String, String> menus = new Hashtable<String, String>();
        
        for(Suite s : platform.getInstalledSuites()) {
            for(Menu m : s.getMenus()) {
                menus.put(m.getId(), m.getName().evaluate());
            }
        }
        
        Hashtable<String, Entry> entries = platform.getMenuMap();
        for(StackFrameStep step : session.getFrame().getSteps()) {
            String val = null; 
            if(SessionFrame.STATE_COMMAND_ID.equals(step.getType())) {
                //Menu or form. 
                if(menus.containsKey(step.getId())) {
                    val = menus.get(step.getId());
                } else if(entries.containsKey(step.getId())) {
                    val = entries.get(step.getId()).getText().evaluate();
                }
            } else if(SessionFrame.STATE_DATUM_VAL.equals(step.getType()) ||
                    SessionFrame.STATE_DATUM_COMPUTED.equals(step.getType())) {
                //nothing much to be done here...
            }
            if(val != null) {
                descriptor += " > " + val;
            }
        }
        
        return descriptor.trim();
    }
    
    public String getTitle() {
        //TODO: Most of this mimicks what we need to do in entrydetail activity, remove it from there
        //and generalize the walking
        
        //TODO: This manipulates the state of the session. We should instead grab and make a copy of the frame, and make a new session to 
        //investigate this.
        
        // Walk backwards until we find something with a long detail
        while (session.getFrame().getSteps().size() > 0 &&
                (!SessionFrame.STATE_DATUM_VAL.equals(session.getNeededData()) ||
                        session.getNeededDatum().getLongDetail() == null)) {
            session.stepBack();
        }
        if(session.getFrame().getSteps().size() == 0) { return null;}
        
        EvaluationContext ec = getEvaluationContext();
        
        //Get the value that was chosen for this item
        String value = session.getPoppedStep().getValue();
        
        SessionDatum datum = session.getNeededDatum();
        
        //Now determine what nodeset that was going to be used to load this select
        TreeReference nodesetRef = datum.getNodeset().clone();
        Vector<XPathExpression> predicates = nodesetRef.getPredicate(nodesetRef.size() -1);
        predicates.add(new XPathEqExpr(true, XPathReference.getPathExpr(datum.getValue()), new XPathStringLiteral(value)));
        
        Vector<TreeReference> elements = ec.expandReference(nodesetRef);
        
        //If we got our ref, awesome. Otherwise we need to bail.
        if(elements.size() != 1 ) { return null;}
        
        //Now generate a context for our element 
        EvaluationContext element = new EvaluationContext(ec, elements.firstElement());
        
        
        //Ok, so get our Text.
        Text t = session.getDetail(datum.getLongDetail()).getTitle().getText();
        boolean isPrettyPrint = true;
        
        //CTS: this is... not awesome.
        //But we're going to use this to test whether we _need_ an evaluation context
        //for this. (If not, the title doesn't have prettyprint for us)
        try {
            String outcome = t.evaluate();
            if(outcome != null) {
                isPrettyPrint = false;
            }
        } catch(Exception e) {
            //Cool. Got us a fancy string.
        }
        
        if(isPrettyPrint) {
            //Now just get the detail title for that element
            return t.evaluate(element);
        } else {
            //Otherwise, this is _almost certainly_ a case. See if it is, and 
            //if so, grab the case name. otherwise, who knows?
            SqlStorage<ACase> storage = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class);
            try {
                ACase ourCase = storage.getRecordForValue(ACase.INDEX_CASE_ID, value);
                if(ourCase != null) {
                    return ourCase.getName();
                } else {
                    return null;
                }
            } catch(Exception e) {
                return null;
            }
        }
    }
    
    /**
     * @return The evaluation context for the current state.
     */
    public EvaluationContext getEvaluationContext() {
        return session.getEvaluationContext(getIIF());
    }
    
    /**
     * @param commandId The id of the command to evaluate against
     * @return The evaluation context relevant for the provided command id 
     */
    public EvaluationContext getEvaluationContext(String commandId) {
        return session.getEvaluationContext(getIIF(), commandId);
    }
    
    AndroidInstanceInitializer initializer;
    protected AndroidInstanceInitializer getIIF() {
        if(initializer == null) {
            initializer = new AndroidInstanceInitializer(session);
        } 
        
        return initializer;
    }

    public static AndroidSessionWrapper mockEasiestRoute(CommCarePlatform platform, String formNamespace, String selectedValue) {
        AndroidSessionWrapper wrapper = null;
        int curPredicates = -1;
        
        Hashtable<String, Entry> menuMap = platform.getMenuMap();
        for(String key : menuMap.keySet()) {
            Entry e = menuMap.get(key);
            if(formNamespace.equals(e.getXFormNamespace())) {
                //We have an entry. Don't worry too much about how we're supposed to get there for now.
                
                //The ideal is that we only need one piece of data
                if(e.getSessionDataReqs().size() == 1) {
                    //This should fit the bill. Single selection.
                    SessionDatum datum = e.getSessionDataReqs().firstElement();
                    
                    //The only thing we need to know now is whether we have a better option available
                    
                    int countPredicates = CommCareUtil.countPreds(datum.getNodeset());
                    
                    if(wrapper == null) {
                        //No previous value! Yay.
                        //Record the degree of specificity of this selection for now (we'll
                        //actually create the wrapper later
                        curPredicates = countPredicates;
                    } else {                        
                        //There's already a path to this form. Only keep going 
                        //if the current choice is less specific
                        if(countPredicates >= curPredicates) { continue;}
                    }
                    
                    wrapper = new AndroidSessionWrapper(platform);
                    wrapper.session.setCommand(platform.getModuleNameForEntry(e));
                    wrapper.session.setCommand(e.getCommandId());
                    wrapper.session.setDatum(datum.getDataId(), selectedValue);
                }
                
                //We don't really have a good thing to do with this yet. For now, just
                //hope there's another easy path to this form
                continue;
            }
        }
        
        return wrapper;
    }

    /**
     * Finish and seal the current session. Run any stack operations mandated by the current entry
     * and pop a new frame from the stack, if one exists.
     */
    public boolean terminateSession() {
        // Possible should re-name this one. We no longer go "home" by default.
        // We might start a new session's frame.

        // CTS: note, this maybe should just be clearing volitiles either way
        // (rather than cherry picking this one), but this is necessary to
        // ensure that stack ops don't re-use the case casedb as the form if the
        // form modified the case database before stack ops fire
        initializer = null;
        

        // Ok, now we just need to figure out if it's time to go home, or time
        // to fire up a new session from the stack
        if(session.finishExecuteAndPop(getEvaluationContext())) {
            //We just built a new session stack into the session, so we want to keep that,
            //clear out the internal state vars, though.
            cleanVolatiles();
            return true;
        } else {
            //start from scratch
            reset();
            return false;
        }
    }
    
    /**
     * Execute a stack action in the current session environment. Note: This action will
     * always require a fresh jump to the central controller.  
     */
    public void executeStackActions(Vector<StackOperation> ops) {
        session.executeStackOperations(ops, getEvaluationContext());
        
        //regardless of whether we just updated the current stack, we need to
        //assume our current volatile states are no longer relevant
        cleanVolatiles();
    }
}
