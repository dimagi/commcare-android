/**
 * 
 */
package org.commcare.android.tasks;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.InvalidStateException;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.cases.model.Case;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.util.CommCarePlatform;
import org.commcare.xml.AndroidCaseXmlParser;
import org.commcare.xml.BestEffortBlockParser;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.MetaDataXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

/**
 * @author ctsims
 *
 */
public abstract class FormRecordCleanupTask<R> extends CommCareTask<Void, Integer, Integer,R> {
	Context context;
	CommCarePlatform platform;
	
	public static final int STATUS_CLEANUP = -1;
	
	private static final int SUCCESS = -1;
	private static final int SKIP = -2;
	private static final int DELETE = -4;
	
	public FormRecordCleanupTask(Context context, CommCarePlatform platform, int taskId) {
		this.context = context;
		this.platform = platform;
		this.taskId = taskId;
	}
	
	
	/*
	 * (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTask#doTaskBackground(java.lang.Object[])
	 */
	@Override
	protected Integer doTaskBackground(Void... params) {
		SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
		
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
		
		this.publishProgress(STATUS_CLEANUP);
		SqlStorage<SessionStateDescriptor> ssdStorage = CommCareApplication._().getUserStorage(SessionStateDescriptor.class);
		
		for(int recordID : recordsToRemove) {
			//We don't know anything about the session yet, so give it -1 to flag that
			wipeRecord(context, -1, recordID, storage, ssdStorage);
		}
		
		System.out.println("Synced: " + unindexedRecords.size() + ". Removed: " + oldrecords + " old records, and " + (recordsToRemove.size() - oldrecords) + " busted new ones");
		return SUCCESS;
	}

	private int cleanupRecord(FormRecord r, SqlStorage<FormRecord> storage) {
		try {
			FormRecord updated = getUpdatedRecord(context, platform, r, FormRecord.STATUS_SAVED);
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
	public static FormRecord getUpdatedRecord(Context context, CommCarePlatform platform, FormRecord r, String newStatus) throws InvalidStateException, InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
		//Awful. Just... awful
		final String[] caseIDs = new String[1];
		final Date[] modified = new Date[] {new Date(0)};
		final String[] uuid = new String[1];
		
		//NOTE: This does _not_ parse and process the case data. It's only for getting meta information
		//about the entry session.
		TransactionParserFactory factory = new TransactionParserFactory() {

			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if(name == null) { return null;}
				if("case".equals(name)) {
					//If we have a proper 2.0 namespace, good.
					if(CaseXmlParser.CASE_XML_NAMESPACE.equals(namespace)) {
						return new AndroidCaseXmlParser(parser, CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class)) {
							
							/*
							 * (non-Javadoc)
							 * @see org.commcare.xml.CaseXmlParser#commit(org.commcare.cases.model.Case)
							 */
							@Override
							public void commit(Case parsed) throws IOException, SessionUnavailableException{
								String incoming = parsed.getCaseId();
								if(incoming != null && incoming != "") {
									caseIDs[0] = incoming;
								}
							}
		
							/*
							 * (non-Javadoc)
							 * @see org.commcare.xml.CaseXmlParser#retrieve(java.lang.String)
							 */
							@Override
							public ACase retrieve(String entityId) throws SessionUnavailableException{
								caseIDs[0] = entityId;
								ACase c = new ACase("","");
								c.setCaseId(entityId);
								return c;
							}
						};
					}else {
					//Otherwise, this gets more tricky. Ideally we'd want to skip this block for compatibility purposes,
					//but we can at least try to get a caseID (which is all we want)
					return new BestEffortBlockParser(parser, null, null, new String[] {"case_id"}) {
						/*
						 * (non-Javadoc)
						 * @see org.commcare.xml.BestEffortBlockParser#commit(java.util.Hashtable)
						 */
						@Override
						public void commit(Hashtable<String, String> values) {
							if(values.containsKey("case_id")) {
								caseIDs[0] = values.get("case_id");
							}
						}
					};}
					
				}
				else if("meta".equals(name.toLowerCase())) {
					return new MetaDataXmlParser(parser) {
						
						/*
						 * (non-Javadoc)
						 * @see org.commcare.xml.MetaDataXmlParser#commit(java.lang.String[])
						 */
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
		
		String path = r.getPath(context);
		
		FileInputStream fis;
		fis = new FileInputStream(path);
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
		
		
		//TODO: We should be committing all changes to form record models via the ASW objects, not manually.
		FormRecord parsed = new FormRecord(r.getInstanceURI().toString(), newStatus, r.getFormNamespace(), r.getAesKey(),uuid[0], modified[0]);
		parsed.setID(r.getID());
		
		//TODO: The platform adds a lot of unfortunate coupling here. Should split out the need to parse completely 
		//uninitialized form records somewhere else.
		
		if(caseIDs[0] != null && r.getStatus().equals(FormRecord.STATUS_UNINDEXED)) {
			AndroidSessionWrapper asw = AndroidSessionWrapper.mockEasiestRoute(platform, r.getFormNamespace(), caseIDs[0]);
			asw.setFormRecordId(parsed.getID());
			
			SqlStorage<SessionStateDescriptor> ssdStorage = CommCareApplication._().getUserStorage(SessionStateDescriptor.class);
			
			//Also bad: this is not synchronous with the parsed record write
			try {
				ssdStorage.write(asw.getSessionStateDescriptor());
			} catch (StorageFullException e) {
			}
		}
		
		//Make sure that the instance is no longer editable
		if(!newStatus.equals(FormRecord.STATUS_INCOMPLETE) && !newStatus.equals(FormRecord.STATUS_UNSTARTED)) {
			ContentValues cv = new ContentValues();
			cv.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, Boolean.toString(false));
			context.getContentResolver().update(r.getInstanceURI(), cv, null, null);
		}
		
		return parsed;
	}
	
	


	public static void wipeRecord(Context c,SessionStateDescriptor existing) {
		int ssid = existing.getID();
		int formRecordId = existing.getFormRecordId();
		wipeRecord(c, ssid, formRecordId);
	}


	public static void wipeRecord(Context c, AndroidSessionWrapper currentState) {
		int formRecordId = currentState.getFormRecordId();
		int ssdId = currentState.getSessionDescriptorId();
		wipeRecord(c, ssdId, formRecordId);
	}
	
	public static void wipeRecord(Context c, FormRecord record) {
		wipeRecord(c, -1, record.getID());
	}
	
	public static void wipeRecord(Context c, int formRecordId) {
		wipeRecord(c, -1, formRecordId);
	}
	
	public static void wipeRecord(Context c, int sessionId, int formRecordId) {
		wipeRecord(c, sessionId, formRecordId, CommCareApplication._().getUserStorage(FormRecord.class), CommCareApplication._().getUserStorage(SessionStateDescriptor.class));
	}
	
	private static void wipeRecord(Context context, int sessionId, int formRecordId, SqlStorage<FormRecord> frStorage, SqlStorage<SessionStateDescriptor> ssdStorage) {

		if(sessionId != -1) {
			try {
				SessionStateDescriptor ssd = ssdStorage.read(sessionId);
				
				int ssdFrid = ssd.getFormRecordId();
				if(formRecordId == -1) {
					formRecordId = ssdFrid;
				} else if(formRecordId != ssdFrid) {
					//Not good.
					Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Inconsistent formRecordId's in session storage");
				}
			} catch(Exception e) {
				Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Session ID exists, but with no record (or broken record)");
			}
		}
		String dataPath = null;
		
		if(formRecordId != -1 ) {
			try {
				FormRecord r = frStorage.read(formRecordId);
				dataPath = r.getPath(context);
				
				//See if there is a hanging session ID for this
				if(sessionId == -1) {
					Vector<Integer> sessionIds = ssdStorage.getIDsForValue(SessionStateDescriptor.META_FORM_RECORD_ID, formRecordId);
					//We really shouldn't be able to end up with sessionId's that point to more than one thing.
					if(sessionIds.size() == 1) {
						sessionId = sessionIds.firstElement();
					} else if(sessionIds.size() > 1) {
						sessionId = sessionIds.firstElement();
						Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Multiple session ID's pointing to the same form record");
					}
				}
			} catch(Exception e) {
				Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Session ID exists, but with no record (or broken record)");
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
