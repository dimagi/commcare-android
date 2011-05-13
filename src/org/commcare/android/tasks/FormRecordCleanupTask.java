/**
 * 
 */
package org.commcare.android.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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
import org.commcare.android.models.Case;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.Referral;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.MetaDataXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
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
			FormRecord updated = getUpdatedRecord(context, r, FormRecord.STATUS_SAVED);
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
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (NoSuchPaddingException e) {
			throw new RuntimeException(e);
		} catch (InvalidKeyException e) {
			throw new RuntimeException(e);
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
		}
	}
	
	/**
	 * Parses out a formrecord and fills in the various parse-able details (UUID, date modified, etc), and updates
	 * it to the provided status.
	 * 
	 * @param context
	 * @param r
	 * @param newStatus
	 * @return null if there was not file to parse. The new form record to be written to the DB otherwise
	 * @throws InvalidKeyException 
	 * @throws NoSuchPaddingException 
	 * @throws NoSuchAlgorithmException 
	 * @throws IOException 
	 * @throws InvalidStructureException 
	 * @throws UnfullfilledRequirementsException 
	 * @throws XmlPullParserException 
	 */
	public static FormRecord getUpdatedRecord(final Context context, FormRecord r, String newStatus) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
		//Awful. Just... awful
		final String[] caseIDs = new String[1];
		final Date[] modified = new Date[] {new Date(0)};
		final String[] uuid = new String[1];
		
		//Note, this factory doesn't handle referral information at all, we're skipping that for now.
		TransactionParserFactory factory = new TransactionParserFactory() {

			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if(name == null) { return null;}
				if("case".equals(name)) {
					return new CaseXmlParser(parser, context) {
						
						@Override
						public void commit(Case parsed) throws IOException, SessionUnavailableException{
							String incoming = parsed.getCaseId();
							if(incoming != null && incoming != "") {
								caseIDs[0] = incoming;
							}
						}

						@Override
						public Case retrieve(String entityId) throws SessionUnavailableException{
							caseIDs[0] = entityId;
							Case c = new Case("","");
							c.setCaseId(entityId);
							return c;
						}
						@Override
						public Referral parseReferral(KXmlParser parser, String caseId, Date modified, Context c) throws SessionUnavailableException, InvalidStructureException, IOException, XmlPullParserException {
							parser.skipSubTree();
							return null;
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
			return null;
		}
		
		FileInputStream fis;
		fis = new FileInputStream(r.getPath());
	
		Cipher decrypter = Cipher.getInstance("AES");
		decrypter.init(Cipher.DECRYPT_MODE, new SecretKeySpec(r.getAesKey(), "AES"));

		CipherInputStream cis = new CipherInputStream(fis, decrypter);

		//Construct parser for this form's internal data.
		DataModelPullParser parser = new DataModelPullParser(cis, factory);
		parser.parse();
		
		FormRecord parsed = new FormRecord(r.getFormNamespace(), r.getPath(), FormRecord.generateEntityId(caseIDs[0]), newStatus, r.getAesKey(),uuid[0], modified[0]);
		parsed.setID(r.getID());
		
		return parsed;
	}


}
