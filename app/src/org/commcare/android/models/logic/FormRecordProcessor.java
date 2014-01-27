/**
 * 
 */
package org.commcare.android.models.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.cases.ledger.Ledger;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.AndroidCaseXmlParser;
import org.commcare.xml.LedgerXmlParsers;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;

/**
 * A FormRecordProcessor keeps track of all of the logic needed to process
 * forms and keep track of models/changes.
 * 
 * TODO: We should move most of the "cleanup" task methods here.
 * 
 * @author ctsims
 *
 */
public class FormRecordProcessor {
	
	private Context c;
	SqlStorage<FormRecord> storage;
	
	public FormRecordProcessor(Context c) {
		this.c = c;
		storage =  CommCareApplication._().getUserStorage(FormRecord.class);
	}

	/**
	 * This is the entry point for processing a form. New transaction types should all be declared here. 
	 * 
	 * @param record
	 * @return
	 * @throws InvalidStructureException
	 * @throws IOException
	 * @throws XmlPullParserException
	 * @throws UnfullfilledRequirementsException
	 * @throws StorageFullException
	 */
	public FormRecord process(FormRecord record) throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException, StorageFullException {
		String form = record.getPath(c);
		
		DataModelPullParser parser;
		final File f = new File(form);

		final Cipher decrypter = FormUploadUtil.getDecryptCipher((new SecretKeySpec(record.getAesKey(), "AES")));
		InputStream is = new CipherInputStream(new FileInputStream(f), decrypter);
		parser = new DataModelPullParser(is, new TransactionParserFactory() {
			
			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if(LedgerXmlParsers.STOCK_XML_NAMESPACE.equals(namespace)) {
					return new LedgerXmlParsers(parser, CommCareApplication._().getUserStorage(Ledger.STORAGE_KEY, Ledger.class));
				}else if(name.toLowerCase().equals("case")) {
					return new AndroidCaseXmlParser(parser, CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class), decrypter, null, f.getParentFile());
				} 
				return null;
			}
			
		}, true, true);
		
		parser.parse();
		is.close();
		
		return updateRecordStatus(record, FormRecord.STATUS_UNSENT);
	}
	
	public FormRecord updateRecordStatus(FormRecord record, String newStatus) throws IOException, StorageFullException{
		//update the records to show that the form has been processed and is ready to be sent;
		record = record.updateStatus(record.getInstanceURI().toString(), newStatus);
		storage.write(record);
		return record;
	}

	public FormRecord getRecord(int dbId) {
		//this seems silly.
		return storage.read(dbId);
	}
	
}
