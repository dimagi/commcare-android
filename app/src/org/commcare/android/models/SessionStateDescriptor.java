/**
 * 
 */
package org.commcare.android.models;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;

import org.commcare.android.database.EncryptedModel;
import org.commcare.android.util.AndroidSessionWrapper;
import org.commcare.util.CommCareSession;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.MD5;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * A Session State Descriptor contains all of the information that can be persisted
 * about a CommCare session. It is immutable and reflects a specific state.
 * 
 * @author ctsims
 *
 */
public class SessionStateDescriptor implements Persistable, IMetaData, EncryptedModel {
	
	public static final String META_DESCRIPTOR_HASH = "descriptorhash";
	
	public static final String META_FORM_RECORD_ID = "form_record_id";
	
	public static final String STORAGE_KEY = "android_cc_session";
	
	private int recordId = -1;
	private int formRecordId = -1;
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

	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		recordId = ExtUtil.readInt(in);
		formRecordId = ExtUtil.readInt(in);
		sessionDescriptor = ExtUtil.readString(in);
	}

	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeNumeric(out, recordId);
		ExtUtil.writeNumeric(out, formRecordId);
		ExtUtil.writeString(out, sessionDescriptor);
	}
		
	public int getFormRecordId() {
		return formRecordId;
	}

	public void setID(int ID) {
		recordId = ID;
	}

	public int getID() {
		return recordId;
	}

	public String[] getMetaDataFields() {
		return new String[] { META_DESCRIPTOR_HASH, META_FORM_RECORD_ID};
	}

	public Hashtable getMetaData() {
		Hashtable data = new Hashtable();
		for(String key : getMetaDataFields()) {
			data.put(key, getMetaData(key));
		}
		return data;
	}

	public Object getMetaData(String fieldName) {
		if(fieldName.equals(META_DESCRIPTOR_HASH)) {
			return getHash();
		}
		if(fieldName.equals(META_FORM_RECORD_ID)) {
			return getFormRecordId();
		}
		throw new IllegalArgumentException("No metadata field " + fieldName  + " for Session Models");
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
