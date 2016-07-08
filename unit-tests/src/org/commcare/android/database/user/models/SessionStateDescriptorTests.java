package org.commcare.android.database.user.models;

import org.commcare.models.AndroidSessionWrapper;
import org.commcare.modern.session.SessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.test.utilities.MockApp;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SessionStateDescriptorTests {

    @Test
    public void testSessionStateDescriptorSerialization() throws Exception {
        MockApp app = new MockApp("/commcare-apps/case_search_and_claim/");
        SessionWrapper session = app.getSession();
        session.setCommand("patient-search");
        session.setQueryDatum(ExternalDataInstance.buildFromRemote("foo", new TreeElement()));
        session.setDatum("case_id", "case_two");
        serializeSessionOutToDescriptor(session);

        session.clearAllState();
        session.setCommand("m0");
        session.setCommand("m0-f0");
        session.setDatum("case_id", "case_two");
        session.setComputedDatum(session.getEvaluationContext());

        serializeSessionOutToDescriptor(session);
    }

    private static void serializeSessionOutToDescriptor(CommCareSession session) {
        AndroidSessionWrapper originalSessionWrapper = new AndroidSessionWrapper(session);
        SessionStateDescriptor oldDescriptor = SessionStateDescriptor.buildFromSessionWrapper(originalSessionWrapper);

        AndroidSessionWrapper newSessionWrapper = new AndroidSessionWrapper(new CommCarePlatform(0, 0));
        newSessionWrapper.loadFromStateDescription(oldDescriptor);

        SessionStateDescriptor newDescriptor = SessionStateDescriptor.buildFromSessionWrapper(newSessionWrapper);
        assertEquals(oldDescriptor.getSessionDescriptor(), newDescriptor.getSessionDescriptor());
    }
}
