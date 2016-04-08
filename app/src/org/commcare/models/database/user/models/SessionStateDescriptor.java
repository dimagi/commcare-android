package org.commcare.models.database.user.models;

import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.modern.models.MetaField;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.StackFrameStep;
import org.javarosa.core.util.MD5;


/**
 * A Session State Descriptor contains all of the information that can be persisted
 * about a CommCare session. It is immutable and reflects a specific state.
 *
 * @author ctsims
 */
@Table(SessionStateDescriptor.STORAGE_KEY)
public class SessionStateDescriptor extends Persisted implements EncryptedModel {

    public static final String STORAGE_KEY = "android_cc_session";

    public static final String META_DESCRIPTOR_HASH = "descriptorhash";

    public static final String META_FORM_RECORD_ID = "form_record_id";

    @Persisting(1)
    @MetaField(value = META_FORM_RECORD_ID, unique = true)
    private int formRecordId = -1;

    @Persisting(2)
    private String sessionDescriptor = null;

    @MetaField(value = META_DESCRIPTOR_HASH)
    public String getHash() {
        return MD5.toHex(MD5.hash(sessionDescriptor.getBytes()));
    }

    //Wrapper for serialization (STILL SKETCHY)
    public SessionStateDescriptor() {

    }

    public SessionStateDescriptor(AndroidSessionWrapper state) {
        this.formRecordId = state.getFormRecordId();
        sessionDescriptor = this.createSessionDescriptor(state.getSession());
    }

    public boolean isEncrypted(String data) {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean isBlobEncrypted() {
        // TODO Auto-generated method stub
        return false;
    }

    public int getFormRecordId() {
        return formRecordId;
    }

    public SessionStateDescriptor reMapFormRecordId(int idForNewRecord) {
        SessionStateDescriptor copy = new SessionStateDescriptor();
        copy.formRecordId = idForNewRecord;
        copy.sessionDescriptor = sessionDescriptor;
        return copy;
    }

    public void fromBundle(String serializedDescriptor) {
        this.sessionDescriptor = serializedDescriptor;
    }

    public String getSessionDescriptor() {
        return this.sessionDescriptor;
    }

    /**
     * Serializes the session into a string which is unique for a
     * given path through the application, and which can be deserialzied
     * back into a live session.
     * <p/>
     * TODO: Currently we rely on this state being semantically unique,
     * but it may change in the future. Rely on the specific format as
     * little as possible.
     */
    private String createSessionDescriptor(CommCareSession session) {
        //TODO: Serialize into something more useful. I dunno. JSON/XML/Something
        String descriptor = "";
        for (StackFrameStep step : session.getFrame().getSteps()) {
            descriptor += step.getType() + " ";
            if (SessionFrame.STATE_COMMAND_ID.equals(step.getType())) {
                descriptor += step.getId() + " ";
            } else if (SessionFrame.STATE_DATUM_VAL.equals(step.getType()) || SessionFrame.STATE_DATUM_COMPUTED.equals(step.getType())) {
                descriptor += step.getId() + " " + step.getValue() + " ";
            } else if (SessionFrame.STATE_QUERY_REQUEST.equals(step.getType())) {
                // for now, don't support restoring sessions that make remote server requests
                descriptor += SessionFrame.STATE_QUERY_REQUEST;
            } else if (SessionFrame.STATE_SYNC_REQUEST.equals(step.getType())) {
                // for now, don't support restoring sessions that make remote server requests
                descriptor += SessionFrame.STATE_SYNC_REQUEST;
            }
        }
        return descriptor.trim();
    }

    public void loadSession(CommCareSession session) {
        String[] tokenStream = sessionDescriptor.split(" ");

        int current = 0;
        while (current < tokenStream.length) {
            String action = tokenStream[current];
            if (action.equals(SessionFrame.STATE_COMMAND_ID)) {
                session.setCommand(tokenStream[++current]);
            } else if (action.equals(SessionFrame.STATE_DATUM_VAL) || action.equals(SessionFrame.STATE_DATUM_COMPUTED)) {
                session.setDatum(tokenStream[++current], tokenStream[++current]);
            } else if (action.equals(SessionFrame.STATE_SYNC_REQUEST)
                    || action.equals(SessionFrame.STATE_QUERY_REQUEST)) {
                // Since restoring sessions with remote requests isn't support,
                // break when encountered and force the user to proceed manually.
                // Shouldn't come up in practice until we build workflows with
                // remote query datums that end in form entry
                break;
            }
            current++;
        }
    }
}
