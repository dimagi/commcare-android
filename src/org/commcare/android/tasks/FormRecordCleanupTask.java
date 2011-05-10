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
import java.util.Date;
import java.util.Hashtable;
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
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.MetaDataXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
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
	
	private static final int SUCCESS = -1;
	private static final int SKIP = -2;
	private static final int DELETE = -4;
	
	public FormRecordCleanupTask(Context context) {
		this.context = context;
	}
	
	
	@Override
	protected Integer doInBackground(Void... params) {
		SqlIndexedStorageUtility<FormRecord> storage = CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		
		Vector<Integer> oldIndexedRecords = storage.getIDsForValues(new String[] { FormRecord.META_STATUS}, new String[] { FormRecord.STATUS_SAVED });
		
		Vector<Integer> unindexedRecords = storage.getIDsForValues(new String[] { FormRecord.META_STATUS}, new String[] { FormRecord.STATUS_UNINDEXED });
		int count = 0;
		for(int recordID : unindexedRecords) {
			FormRecord r = storage.read(recordID);

			switch(cleanupRecord(r, storage)) {
			case SUCCESS:
				break;
			case SKIP:
				break;
			case DELETE:
				storage.remove(recordID);
				break;
			}
			count++;
			this.publishProgress(count);
		}
		
		for(int recordID : oldIndexedRecords) {
			storage.remove(recordID);
		}
		return SUCCESS;
	}
	
	private int cleanupRecord(FormRecord r, SqlIndexedStorageUtility<FormRecord> storage) {
		final Hashtable<String, Case> table = new Hashtable<String, Case>();
		final Date[] modified = new Date[1];
		
		//Note, this factory doesn't handle referral information at all, we're skipping that for now.
		TransactionParserFactory factory = new TransactionParserFactory() {

			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if("case".equals(name)) {
					return new CaseXmlParser(parser, context) {
						
						public void commit(Case parsed) throws IOException, SessionUnavailableException{
							table.put(parsed.getCaseId(), parsed);
						}

						public Case retrieve(String entityId) throws SessionUnavailableException{
							if(table.containsKey(entityId)) {
								return table.get(entityId);
							}
							else{
								return null;
							}
						}
						
						public Referral parseReferral(KXmlParser parser, String caseId, Date modified, Context c) throws SessionUnavailableException, InvalidStructureException, IOException, XmlPullParserException {
							parser.skipSubTree();
							return null;
						}
					};
				}
				else if("meta".equals(name.toLowerCase())) {
					return new MetaDataXmlParser(parser) {
						
						public void commit(Date lastmodified) throws IOException, SessionUnavailableException{
							//table.put(parsed.getCaseId(), parsed);
							modified[0] = lastmodified;
						}

					};
				}
				return null;
			}
			
			
		};
		
		if(!new File(r.getPath()).exists()) {
			//No actual file, delete this
			return DELETE;
		}
		
		FileInputStream fis;
		try {
			fis = new FileInputStream(r.getPath());
		
			Cipher decrypter = Cipher.getInstance("AES");
			decrypter.init(Cipher.DECRYPT_MODE, new SecretKeySpec(r.getAesKey(), "AES"));

			CipherInputStream cis = new CipherInputStream(fis, decrypter);

			//Construct parser for this form's internal data.
			DataModelPullParser parser = new DataModelPullParser(cis, factory);
			parser.parse();

			Case c = null;
			if(table.size() > 0 ) {
				 c = table.values().iterator().next();
			}
			
			if(modified[0] != null) {
				File thefile = new File(r.getPath());
				thefile.setLastModified(modified[0].getTime());
			}
			
			
			//Write record
			FormRecord parsed = new FormRecord(r.getFormNamespace(), r.getPath(), FormRecord.generateEntityId(c), FormRecord.STATUS_SAVED, r.getAesKey());
			parsed.setID(r.getID());
			
			storage.write(parsed);
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


}
