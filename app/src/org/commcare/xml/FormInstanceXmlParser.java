package org.commcare.xml;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.data.xml.TransactionParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

/**
 * @author ctsims
 *
 */
public class FormInstanceXmlParser extends TransactionParser<FormRecord> {

    private final Context c;
    private IStorageUtilityIndexed<FormRecord> storage;

    /**
     * A mapping from an installed form's namespace its install path.
     */
    private final Map<String, String> namespaces;

    private int parseCount = 0;
    private Cipher encrypter;

    /**
     * Root directory for where instances of forms should be saved
     */
    private final String rootInstanceDir;

    public FormInstanceXmlParser(KXmlParser parser, Context c, Map<String, String> namespaces, String destination) {
        super(parser, null, null);
        this.c = c;
        this.namespaces = namespaces;
        this.rootInstanceDir = destination;
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
        
        String filePath = getInstanceDestination(namespaces.get(xmlns));
        
        //Register this instance for inspection
        ContentValues values = new ContentValues();
        values.put(InstanceColumns.DISPLAY_NAME, "Historical Form");
        values.put(InstanceColumns.SUBMISSION_URI, "");
        values.put(InstanceColumns.INSTANCE_FILE_PATH, filePath);
        values.put(InstanceColumns.JR_FORM_ID, xmlns);
        values.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_COMPLETE);
        values.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, false);
        
        Uri instanceRecord = c.getContentResolver().insert(InstanceColumns.CONTENT_URI,values);

        
        FormRecord r = new FormRecord(instanceRecord.toString(), FormRecord.STATUS_UNINDEXED, xmlns, key.getEncoded(),null, new Date(0));
        IStorageUtilityIndexed<FormRecord> storage =  storage();
        
        OutputStream o = new FileOutputStream(filePath);
        BufferedOutputStream bos = null;
        
        try {
            if(encrypter == null) {
                encrypter = Cipher.getInstance(key.getAlgorithm());
            }

            encrypter.init(Cipher.ENCRYPT_MODE, key);
            CipherOutputStream cos = new CipherOutputStream(o, encrypter);
            bos = new BufferedOutputStream(cos,1024*256);
            
        
            serializer.setOutput(bos, "UTF-8");
        
            document.write(serializer);
        
            storage.write(r);
            
        } catch (StorageFullException e) {
            throw new IOException(e.getMessage());
        } 
        //There's nothing we can do about any of these in code, failfast.
        catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            //since bos might not have even been created.
            if(bos != null) {
                bos.close();
            } else {
                o.close();
            }
        }
        return r;
    }
    
    public IStorageUtilityIndexed<FormRecord> storage() throws SessionUnavailableException{
        if(storage == null) {
            storage =  CommCareApplication._().getUserStorage(FormRecord.class);
        } 
        return storage;
    }

    /**
     * Builds the path of where a particular form instance should be stored.
     * Creates a directory using the form's namespace id and the current time
     * returns a path pointing to an xml file of the same name inside that
     * directory.
     *
     * Path should look something like:
     * /app/{app-id}/formdata/{form-id}_{time}/{form-id}_time.xml
     *
     * @param formPath Path to xml file defining a form.
     * @return Absolute path to file where the instance of a given form should
     * be saved.
     */
    private String getInstanceDestination(String formPath) {
        // parseCount makes sure two instances of the same form, parsed in the
        // same second don't get placed in the same file.
        String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime()) + parseCount++;

        String formId = formPath.substring(formPath.lastIndexOf('/') + 1,
                formPath.lastIndexOf('.'));
        String filename = formId + "_" + time;

        String formInstanceDir = rootInstanceDir + filename;
        if (FileUtil.createFolder(formInstanceDir)) {
            return new File(formInstanceDir + "/" + filename + ".xml").getAbsolutePath();
        }

        throw new RuntimeException("Couldn't create folder needed to save form instance");
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.data.xml.TransactionParser#commit(java.lang.Object)
     */
    @Override
    public void commit(FormRecord parsed) throws IOException {
        //This is unused.
    }
}

