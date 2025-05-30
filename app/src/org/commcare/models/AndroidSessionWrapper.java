package org.commcare.models;

import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.models.database.AndroidSandbox;
import org.commcare.models.database.InterruptedFormState;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.session.SessionWrapper;
import org.commcare.modern.session.SessionWrapperInterface;
import org.commcare.modern.util.Pair;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionDescriptorUtil;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.ComputedDatum;
import org.commcare.suite.model.Endpoint;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.FormEntry;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackOperation;
import org.commcare.util.CommCarePlatform;
import org.commcare.utils.AndroidInstanceInitializer;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.CrashUtil;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.analysis.InstanceNameAccumulatingAnalyzer;
import org.javarosa.xpath.analysis.XPathAnalyzable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;

import javax.crypto.SecretKey;

/**
 * This is a container class which maintains all of the appropriate hooks for managing the details
 * of the current "state" of an application (the session, the relevant forms) and the hooks for
 * manipulating them in a single place.
 *
 * @author ctsims
 */
public class AndroidSessionWrapper implements SessionWrapperInterface {
    private static final String TAG = AndroidSessionWrapper.class.getSimpleName();
    //The state descriptor will need these
    private final SessionWrapper session;
    private int formRecordId = -1;
    private int sessionStateRecordId = -1;

    public AndroidSessionWrapper(CommCarePlatform platform) {
        session = new SessionWrapper(new CommCareSession(platform), platform, new AndroidSandbox(CommCareApplication.instance()));
    }

    public AndroidSessionWrapper(SessionWrapper session) {
        this.session = session;
    }

    public void loadFromStateDescription(SessionStateDescriptor descriptor) {
        this.reset();
        this.sessionStateRecordId = descriptor.getID();
        setFormRecordId(descriptor.getFormRecordId());
        SessionDescriptorUtil.loadSessionFromDescriptor(descriptor.getSessionDescriptor(), session);
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

    public void cleanVolatiles() {
        setFormRecordId(-1);
        sessionStateRecordId = -1;
        //CTS - Added to fix bugs where casedb didn't get renewed between sessions (possibly
        //we want to "update" the casedb rather than rebuild it, but this is safest for now.
        initializer = null;
    }

    public SessionWrapper getSession() {
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

        SqlStorage<FormRecord> storage = CommCareApplication.instance().getUserStorage(FormRecord.class);
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
        SessionStateDescriptor ssd = SessionStateDescriptor.buildFromSessionWrapper(this);

        if (!ssd.getSessionDescriptor().contains(SessionFrame.STATE_DATUM_VAL)) {
            // don't continue if the current session doesn't use a case
            return null;
        }

        SqlStorage<SessionStateDescriptor> sessionStorage =
                CommCareApplication.instance().getUserStorage(SessionStateDescriptor.class);

        // TODO: This is really a join situation. Need a way to outline connections between tables to enable joining

        // See if this session's unique hash corresponds to any pending forms.
        Vector<Integer> ids = sessionStorage.getIDsForValue(SessionStateDescriptor.META_DESCRIPTOR_HASH, ssd.getHash());
        for (int id : ids) {
            try {
                if (ssdHasValidFormRecordId(id, sessionStorage)) {
                    return sessionStorage.read(id);
                }
            } catch (NumberFormatException nfe) {
                // TODO: Clean up this record
            }
        }
        return null;
    }

    /**
     *
     */
    public static SessionStateDescriptor getFormStateForInterruptedUserSession() {
        int idOfInterrupted = HiddenPreferences.getIdOfInterruptedSSD();
        if (idOfInterrupted == -1) {
            return null;
        }
        SqlStorage<SessionStateDescriptor> sessionStorage =
                CommCareApplication.instance().getUserStorage(SessionStateDescriptor.class);
        SessionStateDescriptor interrupted = sessionStorage.read(idOfInterrupted);
        return interrupted;
    }

    private static boolean ssdHasValidFormRecordId(int ssdId,
                                                   SqlStorage<SessionStateDescriptor> sessionStorage) {
        SqlStorage<FormRecord> formRecordStorage =
                CommCareApplication.instance().getUserStorage(FormRecord.class);

        int correspondingFormRecordId = Integer.valueOf(
                sessionStorage.getMetaDataFieldForRecord(ssdId, SessionStateDescriptor.META_FORM_RECORD_ID));

        if (!formRecordStorage.exists(correspondingFormRecordId)) {
            sessionStorage.remove(ssdId);
            Log.d(TAG, "Removing stale ssd record: " + ssdId);
            return false;
        }

        return FormRecord.STATUS_INCOMPLETE.equals(
                formRecordStorage.getMetaDataFieldForRecord(correspondingFormRecordId, FormRecord.META_STATUS));
    }

    public void setCurrentStateAsInterrupted(FormIndex formIndex, boolean sessionExpired) {
        if (sessionStateRecordId != -1) {
            SqlStorage<SessionStateDescriptor> sessionStorage =
                    CommCareApplication.instance().getUserStorage(SessionStateDescriptor.class);
            SessionStateDescriptor current = sessionStorage.read(sessionStateRecordId);

            InterruptedFormState interruptedFormState =
                    new InterruptedFormState(current.getID(), formIndex, current.getFormRecordId(), sessionExpired);
            HiddenPreferences.setInterruptedSSD(current.getID());
            HiddenPreferences.setInterruptedFormState(interruptedFormState);
        }
    }

    public void commitStub() {
        //TODO: This should now be locked somehow
        SqlStorage<FormRecord> storage = CommCareApplication.instance().getUserStorage(FormRecord.class);
        SqlStorage<SessionStateDescriptor> sessionStorage = CommCareApplication.instance().getUserStorage(SessionStateDescriptor.class);

        SecretKey key = CommCareApplication.instance().createNewSymmetricKey();

        //TODO: this has two components which can fail. be able to roll them back

        FormRecord r = new FormRecord(FormRecord.STATUS_UNSTARTED, getSession().getForm(),
                key.getEncoded(), null, new Date(0),
                CommCareApplication.instance().getCurrentApp().getAppRecord().getApplicationId());
        storage.write(r);
        setFormRecordId(r.getID());

        SessionStateDescriptor ssd = SessionStateDescriptor.buildFromSessionWrapper(this);
        try {
            sessionStorage.write(ssd);
        } catch (Exception e) {
            if (ssd != null) {
                CrashUtil.log("SessionStateDescriptor form id: " + ssd.getFormRecordId());
            }
            throw e;
        }
        sessionStateRecordId = ssd.getID();
    }

    public int getSessionDescriptorId() {
        return sessionStateRecordId;
    }

    /**
     * @return The evaluation context for the current state.
     */
    public EvaluationContext getEvaluationContext() {
        return session.getEvaluationContext(getIIF());
    }

    @Override
    public EvaluationContext getRestrictedEvaluationContext(String commandId,
                                                            Set<String> instancesToInclude) {
        return session.getEvaluationContext(getIIF(), commandId, instancesToInclude);
    }

    @Override
    public EvaluationContext getEvaluationContextWithAccumulatedInstances(String commandID, XPathAnalyzable xPathAnalyzable) {
        Set<String> instancesNeededForTextCalculation =
                (new InstanceNameAccumulatingAnalyzer()).accumulate(xPathAnalyzable);
        return getRestrictedEvaluationContext(commandID, instancesNeededForTextCalculation);
    }

    @Override
    public void prepareExternalSources() {
        throw new RuntimeException(
                "This method is not yet implemented and only here to maintain parity with core interface");
    }

    /**
     * @param commandId The id of the command to evaluate against
     * @return The evaluation context relevant for the provided command id
     */
    public EvaluationContext getEvaluationContext(String commandId) {
        return session.getEvaluationContext(getIIF(), commandId, null);
    }

    private AndroidInstanceInitializer initializer;

    public AndroidInstanceInitializer getIIF() {
        if (initializer == null) {
            initializer = new AndroidInstanceInitializer(session);
        }

        return initializer;
    }

    @Override
    public String getNeededData() {
        return session.getNeededData(getEvaluationContext());
    }

    @Override
    public SessionDatum getNeededDatum(Entry entry) {
        return session.getNeededDatum(entry);
    }

    public static AndroidSessionWrapper mockEasiestRoute(CommCarePlatform platform, String formNamespace, String selectedValue) {
        AndroidSessionWrapper wrapper = null;
        int curPredicates = -1;

        Hashtable<String, Entry> menuMap = platform.getCommandToEntryMap();
        for (String key : menuMap.keySet()) {
            Entry e = menuMap.get(key);
            if (!(e.isView() || e.isRemoteRequest()) && formNamespace.equals(((FormEntry)e).getXFormNamespace())) {
                //We have an entry. Don't worry too much about how we're supposed to get there for now.

                //The ideal is that we only need one piece of data
                if (e.getSessionDataReqs().size() == 1) {
                    //This should fit the bill. Single selection.
                    SessionDatum datum = e.getSessionDataReqs().firstElement();
                    // we only know how to mock a single case selection
                    if (datum instanceof ComputedDatum) {
                        // Allow mocking of routes that need computed data, useful for case creation forms
                        wrapper = new AndroidSessionWrapper(platform);
                        wrapper.session.setCommand(platform.getModuleNameForEntry((FormEntry)e));
                        wrapper.session.setCommand(e.getCommandId());
                        wrapper.session.setComputedDatum(wrapper.getEvaluationContext());
                    } else if (datum instanceof EntityDatum) {
                        EntityDatum entityDatum = (EntityDatum)datum;
                        //The only thing we need to know now is whether we have a better option available
                        int countPredicates = CommCareUtil.countPreds(entityDatum.getNodeset());

                        if (wrapper == null) {
                            //No previous value! Yay.
                            //Record the degree of specificity of this selection for now (we'll
                            //actually create the wrapper later
                            curPredicates = countPredicates;
                        } else {
                            //There's already a path to this form. Only keep going
                            //if the current choice is less specific
                            if (countPredicates >= curPredicates) {
                                continue;
                            }
                        }

                        wrapper = new AndroidSessionWrapper(platform);
                        wrapper.session.setCommand(platform.getModuleNameForEntry((FormEntry)e));
                        wrapper.session.setCommand(e.getCommandId());
                        wrapper.session.setEntityDatum(entityDatum, selectedValue);
                    }
                }

                //We don't really have a good thing to do with this yet. For now, just
                //hope there's another easy path to this form
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
        if (session.finishExecuteAndPop(getEvaluationContext())) {
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


    public void executeStackActions(Vector<StackOperation> ops) {
        executeStackActions(ops, getEvaluationContext());
    }

    /**
     * Execute a stack action in the current session environment. Note: This action will
     * always require a fresh jump to the central controller.
     */
    public void executeStackActions(Vector<StackOperation> ops, EvaluationContext evaluationContext) {
        session.executeStackOperations(ops, evaluationContext);

        //regardless of whether we just updated the current stack, we need to
        //assume our current volatile states are no longer relevant
        cleanVolatiles();
    }

    public void executeEndpointStack(Endpoint endpoint, ArrayList<String> args) {
        EvaluationContext evaluationContext = getEvaluationContext();
        Endpoint.populateEndpointArgumentsToEvaluationContext(endpoint, args, evaluationContext);
        executeStackActions(endpoint.getStackOperations(), evaluationContext);
    }

    public void executeEndpointStack(Endpoint endpoint, HashMap args) {
        EvaluationContext evaluationContext = getEvaluationContext();
        Endpoint.populateEndpointArgumentsToEvaluationContext(endpoint, args, evaluationContext);
        executeStackActions(endpoint.getStackOperations(), evaluationContext);
    }
}