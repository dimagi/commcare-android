/**
 * 
 */
package org.commcare.android.util;

import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import javax.crypto.SecretKey;

import org.commcare.android.R;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.SessionStateDescriptor;
import org.commcare.android.odk.provider.InstanceProviderAPI;
import org.commcare.android.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareSession;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.storage.StorageFullException;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.database.Cursor;

/**
 * This is a container class which maintains all of the appropriate hooks for managing the details
 * of the current "state" of an application (the session, the relevant forms) and the hooks for 
 * manipulating them in a single place.
 * 
 * @author ctsims
 *
 */
public class AndroidSessionWrapper {
	//The state descriptor will need these 
	protected CommCareSession session;
	protected int formRecordId = -1;
	protected int sessionStateRecordId = -1;
	
	//These are only to be used by the local (not recoverable) session 
	private String instancePath = null;
	private String instanceStatus = null;

	public AndroidSessionWrapper(CommCarePlatform platform) {
		session = new CommCareSession(platform);
	}
	
	/**
	 * Serialize the state of this session so it can be restored 
	 * at a later time. 
	 * 
	 * @return
	 */
	public SessionStateDescriptor getSessionStateDescriptor() {
		return new SessionStateDescriptor(this);
	}
	
	public void loadFromStateDescription(SessionStateDescriptor descriptor) {
		this.sessionStateRecordId = descriptor.getID();
		this.formRecordId = descriptor.getFormRecordId();
	}
	
	/**
	 * Clear all local state and return this session to completely fresh
	 */
	public void reset() {
		this.session.clearState();
		formRecordId = -1;
		instancePath = null;
		instanceStatus = null;
		sessionStateRecordId = -1;
	}

	public CommCareSession getSession() {
		return session;
	}

	public FormRecord getFormRecord() {
		if(formRecordId == -1) {
			return null;
		}
		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		return storage.read(formRecordId);
	}
	
	public void setFormRecordId(int formRecordId) {
		this.formRecordId = formRecordId;
	}
	
	/**
	 * Registers the instance data returned from form entry about this session, and specifies
	 * whether the returned data is complete 
	 * 
	 * @param c A cursor which points to at least one record of an ODK instance.
	 * @return True if the record in question was marked completed, false otherwise
	 * @throws IllegalArgumentException If the cursor provided doesn't point to any records,
	 * or doesn't point to the appropriate columns
	 */
	public boolean beginRecordTransaction(Cursor c) throws IllegalArgumentException {		
        if(!c.moveToFirst()) {
        	throw new IllegalArgumentException("Empty query for instance record!");
        }
        
        instancePath = c.getString(c.getColumnIndexOrThrow(InstanceColumns.INSTANCE_FILE_PATH));
        instanceStatus = c.getString(c.getColumnIndexOrThrow(InstanceColumns.STATUS));

        if(InstanceProviderAPI.STATUS_COMPLETE.equals(instanceStatus)) {
        	return true;
        } else {
        	return false;
        }
	}

	public FormRecord commitRecordTransaction() throws InvalidStateException {
		FormRecord current = getFormRecord();
		String recordStatus = null;
        if(InstanceProviderAPI.STATUS_COMPLETE.equals(instanceStatus)) {
        	recordStatus = FormRecord.STATUS_COMPLETE;
        } else {
        	recordStatus = FormRecord.STATUS_INCOMPLETE;
        }


		current = current.updateStatus(instancePath, recordStatus);
		
		try {
			FormRecord updated = FormRecordCleanupTask.getUpdatedRecord(current, recordStatus);
			
			SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
			storage.write(updated);	
			
			return updated;
		} catch (InvalidStructureException e1) {
			e1.printStackTrace();
			throw new InvalidStateException("Invalid data structure found while parsing form. There's something wrong with the application structure, please contact your supervisor.");
		} catch (IOException e1) {
			throw new InvalidStateException("There was a problem with the local storage and the form could not be read.");
		} catch (XmlPullParserException e1) {
			e1.printStackTrace();
			throw new InvalidStateException("There was a problem with the local storage and the form could not be read.");
		} catch (UnfullfilledRequirementsException e1) {
			throw new RuntimeException(e1);
		} catch (StorageFullException e) {
			throw new RuntimeException(e);
		}
	}

	public int getFormRecordId() {
		return formRecordId;
	}
	
	/**
	 * A helper method to search for any saved sessions which match this current one
	 *  
	 * @return The descriptor of the first saved session which matches this, if any,
	 * null otherwise. 
	 */
	public SessionStateDescriptor searchForDuplicates() {
		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		SqlIndexedStorageUtility<SessionStateDescriptor> sessionStorage = CommCareApplication._().getStorage(SessionStateDescriptor.STORAGE_KEY, SessionStateDescriptor.class);
		
		//TODO: This is really a join situation. Need a way to outline connections between tables to enable joining
		
		//First, we need to see if this session's unique hash corresponds to any pending forms.
		Vector<Integer> ids = sessionStorage.getIDsForValue(SessionStateDescriptor.META_DESCRIPTOR_HASH, getSessionStateDescriptor().getHash());
		
		SessionStateDescriptor ssd = null;
		//Filter for forms which have actually been started.
		for(int id : ids) {
			try {
				int recordId = Integer.valueOf(sessionStorage.getMetaDataFieldForRecord(id, SessionStateDescriptor.META_FORM_RECORD_ID));
				if(!storage.exists(recordId)) {
					sessionStorage.remove(id);
					System.out.println("Removing stale ssd record: " + id);
					continue;
				}
				if(FormRecord.STATUS_INCOMPLETE.equals(storage.getMetaDataFieldForRecord(recordId, FormRecord.META_STATUS))) {
					ssd = sessionStorage.read(id);
					break;
				}
			} catch(NumberFormatException nfe) {
				//TODO: Clean up this record
				continue;
			}
		}
		
		return ssd;
	}

	public void commitStub() throws StorageFullException {
		//TODO: This should now be locked somehow
		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		SqlIndexedStorageUtility<SessionStateDescriptor> sessionStorage = CommCareApplication._().getStorage(SessionStateDescriptor.STORAGE_KEY, SessionStateDescriptor.class);

		SecretKey key = CommCareApplication._().createNewSymetricKey();
		
		//TODO: this has two components which can fail. be able to roll them back
		
		FormRecord r = new FormRecord("", FormRecord.STATUS_UNSTARTED, getSession().getForm(), key.getEncoded(), null, new Date(0));
		storage.write(r);
		setFormRecordId(r.getID());
		
		SessionStateDescriptor ssd = getSessionStateDescriptor();
		sessionStorage.write(ssd);
		sessionStateRecordId = ssd.getID();
	}

	public int getSessionDescriptorId() {
		return sessionStateRecordId;
	}

	public String getHeaderTitle(Context context, AndroidCommCarePlatform platform) {
		String descriptor = context.getString(R.string.app_name);
		Hashtable<String, String> menus = new Hashtable<String, String>();
		for(Suite s : platform.getInstalledSuites()) {
			for(Menu m : s.getMenus()) {
				menus.put(m.getId(), m.getName().evaluate());
			}
		}
		Hashtable<String, Entry> entries = platform.getMenuMap();
		for(String[] step : session.getSteps()) {
			String val = null; 
			if(step[0] == CommCareSession.STATE_COMMAND_ID) {
				//Menu or form. 
				if(menus.containsKey(step[1])) {
					val = menus.get(step[1]);
				} else if(entries.containsKey(step[1])) {
					val = entries.get(step[1]).getText().evaluate();
				}
			} else if(step[0] == CommCareSession.STATE_DATUM_VAL || step[0] == CommCareSession.STATE_DATUM_COMPUTED) {
				//nothing much to be done here...
			}
			if(val != null) {
				descriptor += " > " + val;
			}
		}
		
		return descriptor.trim();
	}
}
