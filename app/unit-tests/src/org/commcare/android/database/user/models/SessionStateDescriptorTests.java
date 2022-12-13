package org.commcare.android.database.user.models;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMultimap;

import org.commcare.models.AndroidSessionWrapper;
import org.commcare.modern.session.SessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.session.RemoteQuerySessionManager;
import org.commcare.test.utilities.MockApp;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.model.instance.ExternalDataInstanceSource;
import org.javarosa.core.model.instance.TreeElement;
import org.junit.Test;

import java.util.ArrayList;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SessionStateDescriptorTests {

    @Test
    public void testSessionStateDescriptorSerialization() throws Exception {
        MockApp app = new MockApp("/commcare-apps/case_search_and_claim/");
        SessionWrapper session = app.getSession();
        session.setCommand("patient-search");
        RemoteQuerySessionManager remoteQuerySessionManager =
                RemoteQuerySessionManager.buildQuerySessionManager(session,
                        session.getEvaluationContext(), new ArrayList<>());
        ExternalDataInstanceSource queryInstanceSource = ExternalDataInstanceSource.buildRemote("foo",
                new TreeElement(), false, "uri",
                ImmutableMultimap.of());
        session.setQueryDatum(queryInstanceSource.toInstance());
        session.setEntityDatum("case_id", "case_two");
        serializeSessionOutToDescriptor(session);

        session.clearAllState();
        session.setCommand("m0");
        session.setCommand("m0-f0");
        session.setEntityDatum("case_id", "case_two");
        session.setComputedDatum(session.getEvaluationContext());

        serializeSessionOutToDescriptor(session);
    }

    private static void serializeSessionOutToDescriptor(CommCareSession session) {
        AndroidSessionWrapper originalSessionWrapper = new AndroidSessionWrapper(session);
        SessionStateDescriptor oldDescriptor = SessionStateDescriptor.buildFromSessionWrapper(originalSessionWrapper);

        AndroidSessionWrapper newSessionWrapper = new AndroidSessionWrapper(new CommCarePlatform(0, 0,0));
        newSessionWrapper.loadFromStateDescription(oldDescriptor);

        SessionStateDescriptor newDescriptor = SessionStateDescriptor.buildFromSessionWrapper(newSessionWrapper);
        assertEquals(oldDescriptor.getSessionDescriptor(), newDescriptor.getSessionDescriptor());
    }
}
