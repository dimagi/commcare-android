package org.commcare.xml;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.data.xml.TransactionParser;
import org.commcare.models.database.SqlStorage;
import org.commcare.utils.FileUtil;
import org.javarosa.xml.util.InvalidStructureException;
import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author ctsims
 */
public class FormInstanceXmlParser extends TransactionParser<FormRecord> {

    private final Context c;
    private SqlStorage<FormRecord> storage;

    /**
     * An unmodifiable mapping from an installed form's namespace its install
     * path.
     */
    private final Map<String, String> namespaceToInstallPath;

    private static int parseCount = 0;

    /**
     * Root directory for where instances of forms should be saved
     */
    private final String rootInstanceDir;

    public FormInstanceXmlParser(KXmlParser parser, Context c,
                                 Map<String, String> namespaceToInstallPath,
                                 String destination) {
        super(parser);
        this.c = c;
        this.namespaceToInstallPath = namespaceToInstallPath;
        this.rootInstanceDir = destination;
    }

    @Override
    public FormRecord parse() throws InvalidStructureException, IOException, XmlPullParserException {
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


        String filePath = getInstanceDestination(namespaceToInstallPath.get(xmlns));

        SqlStorage<FormRecord> formRecordStorage = cachedStorage();
        FormRecord formRecord = FormRecord.getFormRecord(formRecordStorage, filePath);
        if (formRecord == null) {
            throw new RuntimeException("No FormRecord found for file path " + filePath);
        }

        formRecord.setDisplayName("Historical Form");
        formRecord.setStatus(FormRecord.STATUS_COMPLETE);
        formRecord.setCanEditWhenComplete(Boolean.toString(false));
        formRecordStorage.update(formRecord.getID(), formRecord);

        OutputStream o = new FileOutputStream(filePath);
        BufferedOutputStream bos = null;

        try {
            Cipher encrypter = Cipher.getInstance("AES");

            SecretKeySpec key = new SecretKeySpec(formRecord.getAesKey(), "AES");
            encrypter.init(Cipher.ENCRYPT_MODE, key);
            CipherOutputStream cos = new CipherOutputStream(o, encrypter);
            bos = new BufferedOutputStream(cos, 1024 * 256);

            serializer.setOutput(bos, "UTF-8");

            document.write(serializer);
        } catch (InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException e) {
            // writing the form instance to xml failed, so remove the record
            storage.remove(formRecord);
            throw new RuntimeException(e.getMessage());
        } finally {
            //since bos might not have even been created.
            if (bos != null) {
                bos.close();
            } else {
                o.close();
            }
        }
        return formRecord;
    }

    private SqlStorage<FormRecord> cachedStorage() {
        if (storage == null) {
            storage = CommCareApplication.instance().getUserStorage(FormRecord.class);
        }
        return storage;
    }

    /**
     * Path for where a particular form instance should be stored. Creates a
     * directory using the form's namespace id and the current time and returns
     * a path pointing to an xml file of the same name inside that directory.
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

        String formId = formPath.substring(formPath.lastIndexOf(File.separator) + 1,
                formPath.lastIndexOf('.'));
        String filename = formId + "_" + time;

        String formInstanceDir = rootInstanceDir + filename;
        if (FileUtil.createFolder(formInstanceDir)) {
            return new File(formInstanceDir + File.separator + filename + ".xml").getAbsolutePath();
        }

        throw new RuntimeException("Couldn't create folder needed to save form instance");
    }

    @Override
    protected void commit(FormRecord parsed) throws IOException {
        //This is unused.
    }
}

