/**
 * 
 */
package org.commcare.android.database.user.models;

import org.commcare.android.database.EncryptedModel;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
import org.commcare.util.CommCareSession;
import org.javarosa.core.util.MD5;

/**
 * A Session State Descriptor contains all of the information that can be persisted
 * about a CommCare session. It is immutable and reflects a specific state.
 * 
 * @author ctsims
 *
 */
@Table("android_cc_session")
public class SessionStateDescriptor extends Persisted implements EncryptedModel {
	
	public static final String META_DESCRIPTOR_HASH = "descriptorhash";
	
	public static final String META_FORM_RECORD_ID = "form_record_id";

	@Persisting
	@MetaField(value=META_FORM_RECORD_ID, unique=true)
	private int formRecordId = -1;
	
	@Persisting
	@MetaField(value=META_DESCRIPTOR_HASH)
	private String sessionDescriptor = null;
	
	//Wrapper for serialization (STILL SKETCHY)
	public SessionStateDescriptor() {
		
	}
	
	public SessionStateDescriptor(AndroidSessionWrapper state) {
		this.formRecordId = state.getFormRecordId();
		this.sessionDescriptor = this.createSessionDescriptor(state.getSession());
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
	
	public void fromBundle(String serializedDescriptor) {
		this.sessionDescriptor = serializedDescriptor;
	}
	
	public String getHash() {
		return MD5.toHex(MD5.hash(sessionDescriptor.getBytes()));
	}
	
	public String getSessionDescriptor() {
		return this.sessionDescriptor;
	}
	
	private String createSessionDescriptor(CommCareSession session) {
		//TODO: Serialize into something more useful. I dunno. JSON/XML/Something
		String descriptor = "";
		for(String[] step : session.getSteps()) {
			descriptor += step[0] + " ";
			if(step[0] == CommCareSession.STATE_COMMAND_ID) {
				descriptor += step[1] + " ";
			} else if(step[0] == CommCareSession.STATE_DATUM_VAL || step[0] == CommCareSession.STATE_DATUM_COMPUTED) {
				descriptor += step[1] + " " + step[2] + " ";
			}
		}
		return descriptor.trim();
	}
	
	public void loadSession(CommCareSession session) {
		String[] tokenStream = sessionDescriptor.split(" ");
		
		int current = 0;
		while(current < tokenStream.length) {
			String action = tokenStream[current];
			if(action.equals(CommCareSession.STATE_COMMAND_ID)) {
				session.setCommand(tokenStream[++current]);
			} else if(action.equals(CommCareSession.STATE_DATUM_VAL) || action.equals(CommCareSession.STATE_DATUM_COMPUTED)) {
				session.setDatum(tokenStream[++current], tokenStream[++current]);
			}
			current++;
		}
	}
}
