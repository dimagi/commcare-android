/**
 * 
 */
package org.commcare.android.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;

import org.commcare.android.database.EncryptedModel;
import org.commcare.android.models.FormRecord;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareSession;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.MD5;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public class AndroidCommCareSession extends CommCareSession implements Persistable, IMetaData, EncryptedModel {
	
	public static final String META_DESCRIPTOR_HASH = "descriptorhash";
	
	public static final String META_FORM_RECORD_ID = "form_record_id";
	
	public static final String STORAGE_KEY = "android_cc_session";
	private int recordId = -1;
	private int formRecordId = -1;
	
	//Wrapper for serialization (STILL SKETCHY)
	public AndroidCommCareSession() {
		super(null);
	}

	public AndroidCommCareSession(CommCarePlatform platform) {
		super(platform);
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
		formRecordId = ExtUtil.readInt(in);
		//ungreat, still need to implement this
	}

	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeNumeric(out, formRecordId);
		//ungreat, still need to implement this
	}
	
	public void setFormRecord(int formRecordId) {
		this.formRecordId = formRecordId;
	}
	
	public void setFormRecord(FormRecord record) {
		this.formRecordId = record.getID();
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
			return getSessionDescriptorHash();
		}
		if(fieldName.equals(META_FORM_RECORD_ID)) {
			return getFormRecordId();
		}
		throw new IllegalArgumentException("No metadata field " + fieldName  + " for Session Models");
	}
	
	public void clearState() {
		super.clearState();
		recordId = -1;
		formRecordId = -1;
	}
	
	public String getSessionDescriptorHash() {
		return MD5.toHex(MD5.hash(getSessionDescriptor().getBytes()));
	}
	
	public String getSessionDescriptor() {
		String descriptor = "";
		for(String[] step : this.steps) {
			descriptor += step[0] + " ";
			if(step[0] == CommCareSession.STATE_COMMAND_ID) {
				descriptor += step[1] + " ";
			} else if(step[0] == CommCareSession.STATE_DATUM_VAL || step[0] == CommCareSession.STATE_DATUM_COMPUTED) {
				descriptor += step[1] + " " + step[2] + " ";
			}
		}
		return descriptor.trim();
	}
}
