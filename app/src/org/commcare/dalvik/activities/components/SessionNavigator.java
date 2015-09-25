package org.commcare.dalvik.activities.components;

import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.dalvik.activities.EntitySelectActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.Text;
import org.commcare.util.CommCareSession;
import org.commcare.util.SessionFrame;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathException;

import java.util.List;

/**
 * Created by amstone326 on 9/25/15.
 */
public class SessionNavigator {

    // Result codes to be interpreted by the SessionNavigationResponder
    public static final int ASSERTION_FAILURE = 0;
    public static final int REFRESH_UI = 1;
    public static final int START_FORM_ENTRY = 2;
    public static final int GET_COMMAND = 3;
    public static final int START_ENTITY_SELECTION = 4;
    public static final int LAUNCH_CONFIRM_DETAIL = 5;
    public static final int EXCEPTION_THROWN = 6;

    private SessionNavigationResponder responder;
    private TreeReference currentAutoSelectedCase;
    private Exception thrownException;


    public SessionNavigator(SessionNavigationResponder r) {
        this.responder = r;
    }

    private void sendResponse(int resultCode) {
        responder.processSessionResponse(resultCode);
    }

    public TreeReference getCurrentAutoSelection() {
        return this.currentAutoSelectedCase;
    }

    public Exception getCurrentException() {
        return this.thrownException;
    }

    /**
     * Polls the CommCareSession to determine what information is needed in order to proceed with
     * the next entry step in the session, and then executes the action to get that info, OR
     * proceeds with trying to enter the form if no more info is needed
     */
    public void startNextSessionStep() {

        final AndroidSessionWrapper asw = CommCareApplication._().getCurrentSessionWrapper();
        String needed = asw.getSession().getNeededData();

        if (needed == null) {
            readyToProceed(asw);
        } else if (needed.equals(SessionFrame.STATE_COMMAND_ID)) {
            sendResponse(GET_COMMAND);
        } else if (needed.equals(SessionFrame.STATE_DATUM_VAL)) {
            handleGetDatum(asw);
        } else if (needed.equals(SessionFrame.STATE_DATUM_COMPUTED)) {
            handleCompute(asw);
        }
    }

    private void readyToProceed(final AndroidSessionWrapper asw) {

        //See if we failed any of our assertions
        EvaluationContext ec = asw.getEvaluationContext();
        Text text = asw.getSession().getCurrentEntry().getAssertions().getAssertionFailure(ec);
        if (text != null) {
            sendResponse(ASSERTION_FAILURE);
        }

        if (asw.getSession().getForm() == null) {
            if (asw.terminateSession()) {
                startNextSessionStep();
            } else {
                sendResponse(REFRESH_UI);
            }
        } else {
            sendResponse(START_FORM_ENTRY);
        }
    }

    // CommCare needs a case selection to proceed
    private void handleGetDatum(AndroidSessionWrapper asw) {
        TreeReference autoSelection = getAutoSelectedCase(asw);
        if (autoSelection == null) {
            sendResponse(START_ENTITY_SELECTION);
        } else {
            this.currentAutoSelectedCase = autoSelection;
            handleAutoSelect(asw);
        }
    }

    private void handleAutoSelect(AndroidSessionWrapper asw) {
        CommCareSession session = asw.getSession();
        SessionDatum selectDatum = session.getNeededDatum();
        if (selectDatum.getLongDetail() == null) {
            // No confirm detail defined for this entity select, so just set the case id right away
            // and proceed
            String autoSelectedCaseId = EntitySelectActivity.getCaseIdFromReference(
                    currentAutoSelectedCase, selectDatum, asw);
            session.setDatum(selectDatum.getDataId(), autoSelectedCaseId);
            startNextSessionStep();
        } else {
            sendResponse(LAUNCH_CONFIRM_DETAIL);
        }
    }

    /**
     *
     * Returns the auto-selected case for the next needed datum, if there should be one.
     * Returns null if auto selection is not enabled, or if there are multiple available cases
     * for the datum (and therefore auto-selection should not be used).
     */
    private TreeReference getAutoSelectedCase(AndroidSessionWrapper asw) {
        SessionDatum selectDatum = asw.getSession().getNeededDatum();
        if (selectDatum.isAutoSelectEnabled()) {
            EvaluationContext ec = asw.getEvaluationContext();
            List<TreeReference> entityListElements = ec.expandReference(selectDatum.getNodeset());
            if (entityListElements.size() == 1) {
                return entityListElements.get(0);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void handleCompute(AndroidSessionWrapper asw) {
        EvaluationContext ec = asw.getEvaluationContext();
        try {
            asw.getSession().setComputedDatum(ec);
        } catch (XPathException e) {
            this.thrownException = e;
            sendResponse(EXCEPTION_THROWN);
        }
        startNextSessionStep();
    }

}
