package org.commcare.android.database.user.models;

import org.commcare.models.AndroidSessionWrapper;
import org.commcare.android.storage.framework.Persisted;
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

    public SessionStateDescriptor() {

    }

    public static SessionStateDescriptor buildFromSessionWrapper(AndroidSessionWrapper state) {
        SessionStateDescriptor descriptor = new SessionStateDescriptor();
        descriptor.formRecordId = state.getFormRecordId();
        descriptor.sessionDescriptor = createSessionDescriptor(state.getSession());
        return descriptor;
    }

    @Override
    public boolean isEncrypted(String data) {
        return false;
    }

    @Override
    public boolean isBlobEncrypted() {
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
     *
     * NOTE: Currently we rely on this state being semantically unique,
     * but it may change in the future. Rely on the specific format as
     * little as possible.
     */
    private static String createSessionDescriptor(CommCareSession session) {
        StringBuilder descriptor = new StringBuilder();
        for (StackFrameStep step : session.getFrame().getSteps()) {
            String type = step.getType();
            if ((SessionFrame.STATE_QUERY_REQUEST.equals(type) ||
                    SessionFrame.STATE_SYNC_REQUEST.equals(type))) {
                // Skip adding remote server query/sync steps to the descriptor.
                // They are hard to replay (requires serializing query results)
                // and shouldn't be needed for incomplete forms
                continue;
            }
            descriptor.append(type).append(" ");
            if (SessionFrame.STATE_COMMAND_ID.equals(type)) {
                descriptor.append(step.getId()).append(" ");
            } else if (SessionFrame.STATE_DATUM_VAL.equals(type)
                    || SessionFrame.STATE_DATUM_COMPUTED.equals(type)) {
                descriptor.append(step.getId()).append(" ").append(step.getValue()).append(" ");
            }
        }
        return descriptor.toString().trim();
    }

    public void loadSessionFromDescriptor(CommCareSession session) {
        String[] tokenStream = sessionDescriptor.split(" ");

        int current = 0;
        while (current < tokenStream.length) {
            String action = tokenStream[current];
            if (action.equals(SessionFrame.STATE_COMMAND_ID)) {
                session.setCommand(tokenStream[++current]);
            } else if (action.equals(SessionFrame.STATE_DATUM_VAL) ||
                    action.equals(SessionFrame.STATE_DATUM_COMPUTED)) {
                session.setDatum(tokenStream[++current], tokenStream[++current]);
            }
            current++;
        }
    }
}
