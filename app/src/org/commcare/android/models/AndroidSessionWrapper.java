/**
 * 
 */
package org.commcare.android.models;

import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import javax.crypto.SecretKey;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.CommCareUtil;
import org.commcare.android.util.InvalidStateException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;
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
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xpath.expr.XPathEqExpr;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathStringLiteral;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * This is a container class which maintains all of the appropriate hooks for managing the details
 * of the current "state" of an application (the session, the relevant forms) and the hooks for 
 * manipulating them in a single place.
 * 
 * @author ctsims
 *
 */
public class AndroidSessionWrapper {
    //The state descriptor will need these 
    protected CommCareSession session;
    private CommCarePlatform platform;
    protected int formRecordId = -1;
    protected int sessionStateRecordId = -1;
    
    //These are only to be used by the local (not recoverable) session 
    private String instanceUri = null;
    private String instanceStatus = null;

    public AndroidSessionWrapper(CommCarePlatform platform) {
        session = new CommCareSession(platform);
        this.platform = platform;
    }
    
    /**
     * Serialize the state of this session so it can be restored 
     * at a later time. 
     * 
     * @return
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
     * 
     */
    private void cleanVolatiles() {
        formRecordId = -1;
        instanceUri = null;
        instanceStatus = null;
        sessionStateRecordId = -1;
        //CTS - Added to fix bugs where casedb didn't get renewed between sessions (possibly
        //we want to "update" the casedb rather than rebuild it, but this is safest for now.
        initializer = null;
    }
    
    public CommCareSession getSession() {
        return session;
    }

    public FormRecord getFormRecord() {
        if(formRecordId == -1) {
            return null;
        }
        SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
        return storage.read(formRecordId);
    }
    
    public void setFormRecordId(int formRecordId) {
        this.formRecordId = formRecordId;
    }
    
    /**
     * Registers the instance data returned from form entry about this session, and specifies
     * whether the returned data is complete 
     * 
     * @param c A cursor which points to at least one record of an ODK instance.
     * @return True if the record in question was marked completed, false otherwise
     * @throws IllegalArgumentException If the cursor provided doesn't point to any records,
     * or doesn't point to the appropriate columns
     */
    public boolean beginRecordTransaction(Uri uri, Cursor c) throws IllegalArgumentException {        
        if(!c.moveToFirst()) {
            throw new IllegalArgumentException("Empty query for instance record!");
        }
        
        instanceUri = uri.toString();
        instanceStatus = c.getString(c.getColumnIndexOrThrow(InstanceColumns.STATUS));

        if(InstanceProviderAPI.STATUS_COMPLETE.equals(instanceStatus)) {
            return true;
        } else {
            return false;
        }
    }

    public FormRecord commitRecordTransaction() throws InvalidStateException {
        FormRecord current = getFormRecord();
        String recordStatus = null;
        if(InstanceProviderAPI.STATUS_COMPLETE.equals(instanceStatus)) {
            recordStatus = FormRecord.STATUS_COMPLETE;
        } else {
            recordStatus = FormRecord.STATUS_INCOMPLETE;
        }


        current = current.updateStatus(instanceUri, recordStatus);
        
        try {
            FormRecord updated = FormRecordCleanupTask.getUpdatedRecord(CommCareApplication._(), platform, current, recordStatus);
            
            SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
            storage.write(updated);    
            
            return updated;
        } catch (InvalidStructureException e1) {
            e1.printStackTrace();
            throw new InvalidStateException("Invalid data structure found while parsing form. There's something wrong with the application structure, please contact your supervisor.");
        } catch (IOException e1) {
            throw new InvalidStateException("There was a problem with the local storage and the form could not be read.");
        } catch (XmlPullParserException e1) {
            e1.printStackTrace();
            throw new InvalidStateException("There was a problem with the local storage and the form could not be read.");
        } catch (UnfullfilledRequirementsException e1) {
            throw new RuntimeException(e1);
        } catch (StorageFullException e) {
            throw new RuntimeException(e);
        }
    }

    public int getFormRecordId() {
        return formRecordId;
    }
    
    /**
     * A helper method to search for any saved sessions which match this current one
     *  
     * @return The descriptor of the first saved session which matches this, if any,
     * null otherwise. 
     */
    public SessionStateDescriptor searchForDuplicates() {
        SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
        SqlStorage<SessionStateDescriptor> sessionStorage = CommCareApplication._().getUserStorage(SessionStateDescriptor.class);
        
        //TODO: This is really a join situation. Need a way to outline connections between tables to enable joining
        
        //First, we need to see if this session's unique hash corresponds to any pending forms.
        Vector<Integer> ids = sessionStorage.getIDsForValue(SessionStateDescriptor.META_DESCRIPTOR_HASH, getSessionStateDescriptor().getHash());
        
        SessionStateDescriptor ssd = null;
        //Filter for forms which have actually been started.
        for(int id : ids) {
            try {
                int recordId = Integer.valueOf(sessionStorage.getMetaDataFieldForRecord(id, SessionStateDescriptor.META_FORM_RECORD_ID));
                if(!storage.exists(recordId)) {
                    sessionStorage.remove(id);
                    System.out.println("Removing stale ssd record: " + id);
                    continue;
                }
                if(FormRecord.STATUS_INCOMPLETE.equals(storage.getMetaDataFieldForRecord(recordId, FormRecord.META_STATUS))) {
                    ssd = sessionStorage.read(id);
                    break;
                }
            } catch(NumberFormatException nfe) {
                //TODO: Clean up this record
                continue;
            }
        }
        
        return ssd;
    }

    public void commitStub() throws StorageFullException {
        //TODO: This should now be locked somehow
        SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
        SqlStorage<SessionStateDescriptor> sessionStorage = CommCareApplication._().getUserStorage(SessionStateDescriptor.class);

        SecretKey key = CommCareApplication._().createNewSymetricKey();
        
        //TODO: this has two components which can fail. be able to roll them back

        String form = getSession().getForm();
        // COMMCARE-163637: in landscape mode, this returns null, and the FormRecord should not have any null fields
        form = form != null ? form : "";

        FormRecord r = new FormRecord("", FormRecord.STATUS_UNSTARTED, form, key.getEncoded(), null, new Date(0));
        Log.i("FormRecord","FormRecord is: " + r + ", session: " + getSession() + " | form: " + getSession().getForm() + " | key enc: " + key.getEncoded());
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
            if(step.getType() == SessionFrame.STATE_COMMAND_ID) {
                //Menu or form. 
                if(menus.containsKey(step.getId())) {
                    val = menus.get(step.getId());
                } else if(entries.containsKey(step.getId())) {
                    val = entries.get(step.getId()).getText().evaluate();
                }
            } else if(step.getType() == SessionFrame.STATE_DATUM_VAL || step.getType() == SessionFrame.STATE_DATUM_COMPUTED) {
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
        
        //Walk backwards until we find something with a long detail
        while(session.getFrame().getSteps().size() > 0 && (session.getNeededData() != SessionFrame.STATE_DATUM_VAL || session.getNeededDatum().getLongDetail() == null)) {
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
    
    public EvaluationContext getEvaluationContext() {
        return session.getEvaluationContext(getIIF());
    }
    
    CommCareInstanceInitializer initializer;
    protected CommCareInstanceInitializer getIIF() {
        if(initializer == null) {
            initializer = new CommCareInstanceInitializer(session);
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
                    wrapper.session.setCommand(key);
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
        //Possible should re-name this one. We no longer go "home" by default. We might start a new session's frame.
        
        //CTS: note, this maybe should just be clearing volitiles either way (rather than cherry picking this one),
        //but this is necessary to ensure that stack ops don't re-use the case casedb as the form if the form
        //modified the case database before stack ops fire
        initializer = null;
        
        //TODO: should this section get wrapped up in the session, maybe?
        Vector<StackOperation> ops = session.getCurrentEntry().getPostEntrySessionOperations();
        
        //Let the session know that the current frame shouldn't work its way back onto the stack
        session.markCurrentFrameForDeath();
        
        //First, see if we have operations to run
        if(ops.size() > 0) {
            EvaluationContext ec = getEvaluationContext();
            session.executeStackOperations(ops, ec);
        }
        
        //Ok, now we just need to figure out if it's time to go home, or time to fire up a new session from the stack
        if(session.finishAndPop()) {
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
