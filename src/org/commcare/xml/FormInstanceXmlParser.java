/**
 * 
 */
package org.commcare.xml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Hashtable;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.FormRecord;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.data.xml.TransactionParser;
import org.commcare.xml.util.InvalidStructureException;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;

/**
 * @author ctsims
 *
 */
public class FormInstanceXmlParser extends TransactionParser<FormRecord> {

	Context c;
	IStorageUtilityIndexed storage;
	Hashtable<String, String> namespaces;
	int counter = 0;
	
	private String destination;
	
	public FormInstanceXmlParser(KXmlParser parser, Context c, Hashtable<String, String> namespaces) {
		super(parser, null, null);
		this.c = c;
		this.namespaces = namespaces;
		destination = CommCareApplication._().fsPath(GlobalConstants.FILE_CC_SAVED);
	}
	
	@Override
	public boolean parses(String name, String namespace) {
		if(namespaces.containsKey(namespace)) {
			return true;
		}
		return false;
	}


	public FormRecord parse() throws InvalidStructureException, IOException, XmlPullParserException, SessionUnavailableException {
		String xmlns = parser.getNamespace();
		//Parse this subdocument into a dom
		Element element = new Element();
		element.setName(parser.getName());
		element.setNamespace(parser.getNamespace());
		element.parse(this.parser);
		
		//Consume the end tag.
		//this.parser.next();
		
		//create an actual document out of it.
		Document document = new Document();
		document.addChild(Node.ELEMENT, element);	
		
		KXmlSerializer serializer = new KXmlSerializer();
	
		SecretKey key = CommCareApplication._().createNewSymetricKey();
		FormRecord r = new FormRecord(xmlns, getFileDestination(namespaces.get(xmlns), destination)  , "", FormRecord.STATUS_UNINDEXED, key.getEncoded());
		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		
		OutputStream o = new FileOutputStream(r.getPath());
		CipherOutputStream cos = null;
		
		Cipher encrypter;
		try {
			encrypter = Cipher.getInstance(key.getAlgorithm());

			encrypter.init(Cipher.ENCRYPT_MODE, key);
			cos = new CipherOutputStream(o, encrypter);
		
			serializer.setOutput(cos, "UTF-8");
		
			document.write(serializer);
		
			storage.write(r);
			
		} catch (StorageFullException e) {
			throw new IOException(e.getMessage());
		} 
		//There's nothing we can do about any of these in code, failfast.
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e.getMessage());
		} catch (NoSuchPaddingException e) {
			throw new RuntimeException(e.getMessage());
		} catch (InvalidKeyException e) {
			throw new RuntimeException(e.getMessage());
		} finally {
			//since cos might not have even been created.
			if(cos != null) {
				cos.close();
			} else {
				o.close();
			}
		}
		return r;
	}
	
	public IStorageUtilityIndexed storage() throws SessionUnavailableException{
		if(storage == null) {
			storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		} 
		return storage;
	}
	
	private String getFileDestination(String formPath, String instancePath) {
        // Create new answer folder.
        String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime()) + counter;
        String file = formPath.substring(formPath.lastIndexOf('/') + 1, formPath.lastIndexOf('.'));
        counter++;
        
        String path = instancePath + file + "_" + time;
        if (FileUtil.createFolder(path)) {
            return new File(path + "/" + file + "_" + time + ".xml").getAbsolutePath();
        }
        throw new RuntimeException("Couldn't create folder needed to save form instance");
	}

	@Override
	public void commit(FormRecord parsed) throws IOException {
		//This is unused.
	}
}

