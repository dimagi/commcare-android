package org.commcare.xml;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.services.CommCareKeyManager;
import org.commcare.data.xml.TransactionParser;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.utils.FileUtil;
import org.javarosa.xml.util.InvalidStructureException;
import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

/**
 * @author ctsims
 */
public class FormInstanceXmlParser extends TransactionParser<FormRecord> {

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

    public FormInstanceXmlParser(KXmlParser parser,
                                 Map<String, String> namespaceToInstallPath,
                                 String destination) {
        super(parser);
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
        SqlStorage<FormRecord> formRecordStorage = cachedStorage();

        FormRecord formRecord = new FormRecord(FormRecord.STATUS_UNINDEXED,
                xmlns,
                CommCareKeyManager.generateLegacyKeyOrEmpty(),
                null,
                new Date(0),
                CommCareApplication.instance().getCurrentApp().getAppRecord().getApplicationId());


        formRecord.setDisplayName("Historical Form");

        String filePath = getInstanceDestination(namespaceToInstallPath.get(xmlns));
        formRecord.setFilePath(filePath);
        formRecordStorage.write(formRecord);

        OutputStream os;
        try {
            os = openEncryptedOutputStream(formRecord, filePath);
        } catch (RuntimeException e) {
            // writing the form instance to xml failed, so remove the record
            storage.remove(formRecord);
            throw e;
        }

        try {
            serializer.setOutput(os, "UTF-8");
            document.write(serializer);
        } finally {
            os.close();
        }
        return formRecord;
    }

    private static OutputStream openEncryptedOutputStream(FormRecord formRecord, String filePath)
            throws FileNotFoundException {
        if (formRecord.usesKeystoreEncryption()) {
            return EncryptionIO.createFileOutputStreamWithKeystore(
                    filePath, CommCareKeyManager.retrieveSessionKeyAndTransformation());
        }
        return EncryptionIO.createFileOutputStream(
                filePath, new SecretKeySpec(formRecord.getAesKey(), "AES"));
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

