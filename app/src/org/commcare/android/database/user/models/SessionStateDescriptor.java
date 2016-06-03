package org.commcare.android.database.user.models;

import org.commcare.android.storage.framework.PersistedPlain;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.StackFrameStep;
import org.javarosa.core.util.MD5;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A Session State Descriptor contains all of the information that can be persisted
 * about a CommCare session. It is immutable and reflects a specific state.
 *
 * @author ctsims
 */
@Table(SessionStateDescriptor.STORAGE_KEY)
public class SessionStateDescriptor extends PersistedPlain implements EncryptedModel {

    public static final String STORAGE_KEY = "android_cc_session";

    public static final String META_DESCRIPTOR_HASH = "descriptorhash";
    // TODO PLM: mark this as unique!
    public static final String META_FORM_RECORD_ID = "form_record_id";

    private int formRecordId = -1;
    private String sessionDescriptor = null;

    //Wrapper for serialization (STILL SKETCHY)
    public SessionStateDescriptor() {

    }

    public SessionStateDescriptor(AndroidSessionWrapper state) {
        this.formRecordId = state.getFormRecordId();
        sessionDescriptor = this.createSessionDescriptor(state.getSession());
    }

    public String getHash() {
        return MD5.toHex(MD5.hash(sessionDescriptor.getBytes()));
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
     * <p/>
     * TODO: Currently we rely on this state being semantically unique,
     * but it may change in the future. Rely on the specific format as
     * little as possible.
     */
    private String createSessionDescriptor(CommCareSession session) {
        //TODO: Serialize into something more useful. I dunno. JSON/XML/Something
        StringBuilder descriptor = new StringBuilder();
        for (StackFrameStep step : session.getFrame().getSteps()) {
            descriptor.append(step.getType());
            if (SessionFrame.STATE_COMMAND_ID.equals(step.getType())) {
                descriptor.append(step.getId());
            } else if (SessionFrame.STATE_DATUM_VAL.equals(step.getType()) || SessionFrame.STATE_DATUM_COMPUTED.equals(step.getType())) {
                descriptor.append(step.getId()).append(" ").append(step.getValue());
            } else if (SessionFrame.STATE_QUERY_REQUEST.equals(step.getType())) {
                // for now, don't support restoring sessions that make remote server requests
                descriptor.append( SessionFrame.STATE_QUERY_REQUEST);
            } else if (SessionFrame.STATE_SYNC_REQUEST.equals(step.getType())) {
                // for now, don't support restoring sessions that make remote server requests
                descriptor.append(SessionFrame.STATE_SYNC_REQUEST);
            }
            descriptor.append(" ");
        }
        return descriptor.toString().trim();
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

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);

        formRecordId = ExtUtil.readInt(in);
        sessionDescriptor = ExtUtil.readString(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);

        ExtUtil.writeNumeric(out, formRecordId);
        ExtUtil.writeString(out, sessionDescriptor);
    }

    @Override
    public String[] getMetaDataFields() {
        return new String[]{META_DESCRIPTOR_HASH, META_FORM_RECORD_ID};
    }

    @Override
    public Object getMetaData(String fieldName) {
        switch (fieldName) {
            case META_DESCRIPTOR_HASH:
                return getHash();
            case META_FORM_RECORD_ID:
                return formRecordId;
            default:
                throw new IllegalArgumentException("No metadata field " + fieldName + " in the storage system");
        }
    }
}
