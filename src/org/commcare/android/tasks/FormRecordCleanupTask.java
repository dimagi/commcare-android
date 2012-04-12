/**
 * 
 */
package org.commcare.android.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.ACase;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.SessionStateDescriptor;
import org.commcare.android.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.android.util.AndroidSessionWrapper;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.InvalidStateException;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.cases.model.Case;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.AndroidCaseXmlParser;
import org.commcare.xml.MetaDataXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

/**
 * @author ctsims
 *
 */
public class FormRecordCleanupTask extends AsyncTask<Void, Integer, Integer> {
	Context context;
	
	public static final int STATUS_CLEANUP = -1;
	
	private static final int SUCCESS = -1;
	private static final int SKIP = -2;
	private static final int DELETE = -4;
	
	public FormRecordCleanupTask(Context context) {
		this.context = context;
	}
	
	
	@Override
	protected Integer doInBackground(Void... params) {
		SqlIndexedStorageUtility<FormRecord> storage = CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		
		Vector<Integer> recordsToRemove = storage.getIDsForValues(new String[] { FormRecord.META_STATUS}, new String[] { FormRecord.STATUS_SAVED });
		
		Vector<Integer> unindexedRecords = storage.getIDsForValues(new String[] { FormRecord.META_STATUS}, new String[] { FormRecord.STATUS_UNINDEXED });
		int oldrecords = recordsToRemove.size();
		int count = 0;
		for(int recordID : unindexedRecords) {
			FormRecord r = storage.read(recordID);

			switch(cleanupRecord(r, storage)) {
			case SUCCESS:
				break;
			case SKIP:
				break;
			case DELETE:
				recordsToRemove.add(recordID);
				break;
			}
			count++;
			this.publishProgress(count, unindexedRecords.size());
		}
		
		List<Integer> wipeable = new ArrayList<Integer>();
		this.publishProgress(STATUS_CLEANUP);
		for(int recordID : recordsToRemove) {
			removeRecordThorough(recordID, storage, wipeable);
		}
		
		storage.remove(wipeable);
		System.out.println("Synced: " + unindexedRecords.size() + ". Removed: " + oldrecords + " old records, and " + (recordsToRemove.size() - oldrecords) + " busted new ones");
		return SUCCESS;
	}
	
	private void removeRecordThorough(int recordID, SqlIndexedStorageUtility<FormRecord> storage, List<Integer> toRemove) {
		String path = storage.getMetaDataFieldForRecord(recordID, FormRecord.META_PATH);
		if(path != null && path != "") {
			File file = new File(path).getParentFile();
			if(file.exists()) {
				if(!FileUtil.deleteFile(file)) {
					//Don't remove the record pointer if the file didn't get deleted, since we'll
					//lose the ability to clear it later.
					return;
				}
			}
		}
		toRemove.add(recordID);
	}


	private int cleanupRecord(FormRecord r, SqlIndexedStorageUtility<FormRecord> storage) {
		try {
			FormRecord updated = getUpdatedRecord(r, FormRecord.STATUS_SAVED);
			if(updated == null) {
				return DELETE;
			} else {
				storage.write(updated);
			}
			return SUCCESS;
		} catch (FileNotFoundException e) {
			// No form, skip and delete the form record;
			e.printStackTrace();
			return DELETE;
		} catch (InvalidStructureException e) {
			// Bad form data, skip and delete
			e.printStackTrace();
			return DELETE;
		} catch (IOException e) {
			e.printStackTrace();
			// No idea, might be temporary, Skip
			return SKIP;
		} catch (XmlPullParserException e) {
			e.printStackTrace();
			// No idea, might be temporary, Skip
			return SKIP;
		} catch (UnfullfilledRequirementsException e) {
			e.printStackTrace();
			// Can't resolve here, skip.
			return SKIP;
		} catch (StorageFullException e) {
			// Can't resolve here, skip.
			throw new RuntimeException(e);
		} catch (InvalidStateException e) {
			//Bad situation going down, wipe out the record
			return DELETE;
		}
	}
	
	/**
	 * Parses out a formrecord and fills in the various parse-able details (UUID, date modified, etc), and updates
	 * it to the provided status.
	 * 
	 * @param context
	 * @param r
	 * @param newStatus
	 * @return The new form record containing relevant details about this form
	 * @throws InvalidKeyException 
	 * @throws NoSuchPaddingException 
	 * @throws NoSuchAlgorithmException 
	 * @throws IOException 
	 * @throws InvalidStructureException 
	 * @throws UnfullfilledRequirementsException 
	 * @throws XmlPullParserException 
	 */
	public static FormRecord getUpdatedRecord(FormRecord r, String newStatus) throws InvalidStateException, InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
		//Awful. Just... awful
		final String[] caseIDs = new String[1];
		final Date[] modified = new Date[] {new Date(0)};
		final String[] uuid = new String[1];
		
		//Note, this factory doesn't handle referral information at all, we're skipping that for now.
		TransactionParserFactory factory = new TransactionParserFactory() {

			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if(name == null) { return null;}
				if("case".equals(name)) {
					return new AndroidCaseXmlParser(parser, CommCareApplication._().getStorage(ACase.STORAGE_KEY, ACase.class)) {
						
						@Override
						public void commit(Case parsed) throws IOException, SessionUnavailableException{
							String incoming = parsed.getCaseId();
							if(incoming != null && incoming != "") {
								caseIDs[0] = incoming;
							}
						}

						@Override
						public ACase retrieve(String entityId) throws SessionUnavailableException{
							caseIDs[0] = entityId;
							ACase c = new ACase("","");
							c.setCaseId(entityId);
							return c;
						}
					};
				}
				else if("meta".equals(name.toLowerCase())) {
					return new MetaDataXmlParser(parser) {
						
						@Override
						public void commit(String[] meta) throws IOException, SessionUnavailableException{
							if(meta[0] != null) {
								modified[0] = DateUtils.parseDateTime(meta[0]);
							}
							uuid[0] = meta[1];
						}

					};
				}
				return null;
			}
			
			
		};
		
		if(!new File(r.getPath()).exists()) {
			throw new InvalidStateException("No file exists for form record at: " + r.getPath());
		}
		
		FileInputStream fis;
		fis = new FileInputStream(r.getPath());
		InputStream is = fis;
		
		try {
			Cipher decrypter = Cipher.getInstance("AES");
			decrypter.init(Cipher.DECRYPT_MODE, new SecretKeySpec(r.getAesKey(), "AES"));		
			is = new CipherInputStream(fis, decrypter);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new RuntimeException("No Algorithm while attempting to decode form submission for processing");
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
			throw new RuntimeException("Invalid cipher data while attempting to decode form submission for processing");
		} catch (InvalidKeyException e) {
			e.printStackTrace();
			throw new RuntimeException("Invalid Key Data while attempting to decode form submission for processing");
		}

		//Construct parser for this form's internal data.
		DataModelPullParser parser = new DataModelPullParser(is, factory);
		parser.parse();
		
		FormRecord parsed = new FormRecord(r.getPath(), newStatus, r.getFormNamespace(), r.getAesKey(),uuid[0], modified[0]);
		parsed.setID(r.getID());
		
		return parsed;
	}


	public void wipeRecord(SessionStateDescriptor existing) {
		int ssid = existing.getID();
		int formRecordId = existing.getFormRecordId();
		wipeRecord(ssid, formRecordId);
	}


	public void wipeRecord(AndroidSessionWrapper currentState) {
		int formRecordId = currentState.getFormRecordId();
		int ssdId = currentState.getSessionDescriptorId();
		wipeRecord(ssdId, formRecordId);
	}
	
	public void wipeRecord(FormRecord record) {
		wipeRecord(-1, record.getID());
	}
	
	private void wipeRecord(int sessionId, int formRecordId) {
		SqlIndexedStorageUtility<FormRecord> frStorage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		SqlIndexedStorageUtility<SessionStateDescriptor> ssdStorage = CommCareApplication._().getStorage(SessionStateDescriptor.STORAGE_KEY, SessionStateDescriptor.class);

		if(sessionId != -1) {
			try {
				SessionStateDescriptor ssd = ssdStorage.read(sessionId);
				
				int ssdFrid = ssd.getFormRecordId();
				if(formRecordId == -1) {
					formRecordId = ssdFrid;
				} else if(formRecordId != ssdFrid) {
					//Not good.
					Logger.log("record-cleanup", "Inconsistent formRecordId's in session storage");
				}
			} catch(Exception e) {
				Logger.log("record-cleanup", "Session ID exists, but with no record (or broken record)");
			}
		}
		String dataPath = null;
		
		if(formRecordId != -1 ) {
			try {
				FormRecord r = frStorage.read(formRecordId);
				dataPath = r.getPath();
				
				//See if there is a hanging session ID for this
				if(sessionId == -1) {
					Vector<Integer> sessionIds = ssdStorage.getIDsForValue(SessionStateDescriptor.META_FORM_RECORD_ID, formRecordId);
					//We really shouldn't be able to end up with sessionId's that point to more than one thing.
					if(sessionIds.size() == 1) {
						sessionId = sessionIds.firstElement();
					} else if(sessionIds.size() > 1) {
						sessionId = sessionIds.firstElement();
						Logger.log("record-cleanup", "Multiple session ID's pointing to the same form record");
					}
				}
			} catch(Exception e) {
				Logger.log("record-cleanup", "Session ID exists, but with no record (or broken record)");
			}
		}
		
		//Delete 'em if you got 'em
		if(sessionId != -1) {
			ssdStorage.remove(sessionId);
		}
		if(formRecordId != -1) {
			frStorage.remove(formRecordId);
		}
		
		if(dataPath != null) {
			String selection = InstanceColumns.INSTANCE_FILE_PATH +"=?";
			Cursor c = context.getContentResolver().query(InstanceColumns.CONTENT_URI, new String[] {InstanceColumns._ID}, selection, new String[] {dataPath}, null);
			if(c.moveToFirst()) {
				//There's a cursor for this file, good.
				long id = c.getLong(0);
				
				//this should take care of the files
				context.getContentResolver().delete(ContentUris.withAppendedId(InstanceColumns.CONTENT_URI, id), null, null);
				c.close();
			} else{
				//No instance record for whatever reason, manually wipe files
				FileUtil.deleteFileOrDir(dataPath);
			}
		}
	}
}
