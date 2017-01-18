package org.commcare.android.database.user.models;

import org.commcare.models.AndroidSessionWrapper;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.modern.models.MetaField;
import org.commcare.session.SessionDescriptorUtil;
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
        descriptor.sessionDescriptor = SessionDescriptorUtil.createSessionDescriptor(state.getSession());
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
}
