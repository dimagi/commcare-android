package org.commcare.android.database.user.models;

import org.commcare.android.database.EncryptedModel;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
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
@Table("android_cc_session")
public class SessionStateDescriptor extends Persisted implements EncryptedModel {

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
            if (step.getType() == SessionFrame.STATE_COMMAND_ID) {
                descriptor += step.getId() + " ";
            } else if (step.getType() == SessionFrame.STATE_DATUM_VAL || step.getType() == SessionFrame.STATE_DATUM_COMPUTED) {
                descriptor += step.getId() + " " + step.getValue() + " ";
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
            }
            current++;
        }
    }
}
