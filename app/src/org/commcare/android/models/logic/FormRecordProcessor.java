package org.commcare.android.models.logic;

import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import org.commcare.android.cases.CaseUtils;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.core.process.XmlFormRecordProcessor;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.data.xml.TransactionParser;
import org.commcare.xml.AndroidTransactionParserFactory;
import org.commcare.xml.LedgerXmlParsers;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;

/**
 * A FormRecordProcessor keeps track of all of the logic needed to process
 * forms and keep track of models/changes.
 *
 * TODO: We should move most of the "cleanup" task methods here.
 *
 * @author ctsims
 */
public class FormRecordProcessor {

    private final Context c;
    private final SqlStorage<FormRecord> storage;

    private boolean isBulkProcessing = false;

    private boolean isPurgePending = false;

    public FormRecordProcessor(Context c) {
        this.c = c;
        storage = CommCareApplication._().getUserStorage(FormRecord.class);
    }

    /**
     * This is the entry point for processing a form. New transaction types
     * should all be declared here.
     */
    public FormRecord process(FormRecord record)
            throws InvalidStructureException, IOException, XmlPullParserException,
            UnfullfilledRequirementsException {
        String form = record.getPath(c);

        final File f = new File(form);

        final Cipher decrypter =
                FormUploadUtil.getDecryptCipher((new SecretKeySpec(record.getAesKey(), "AES")));
        InputStream is = new CipherInputStream(new FileInputStream(f), decrypter);

        AndroidTransactionParserFactory factory = new AndroidTransactionParserFactory(c, null) {
            @Override
            public TransactionParser getParser(KXmlParser parser) {
                String namespace = parser.getNamespace();
                String name = parser.getName();
                if (LedgerXmlParsers.STOCK_XML_NAMESPACE.equals(namespace) || "case".equalsIgnoreCase(name)) {
                    return super.getParser(parser);
                } else {
                    return null;
                }
            }
        };

        XmlFormRecordProcessor.process(null, is, factory);

        //Let anyone who is listening know!
        Intent i = new Intent("org.commcare.dalvik.api.action.data.update");
        this.c.sendBroadcast(i);

        //Update the record before trying to purge, so we don't block on this, in case
        //anything weird happens. We don't want to get into a loop
        FormRecord updatedRecord =  updateRecordStatus(record, FormRecord.STATUS_UNSENT);

        if(factory.wereCaseIndexesDisrupted()) {
            if(isBulkProcessing) {
                isPurgePending = true;
            } else {
                performPurge();
            }
        }

        return updatedRecord;
    }

    public FormRecord updateRecordStatus(FormRecord record, String newStatus) {
        // update the records to show that the form has been processed and is
        // ready to be sent;
        record = record.updateInstanceAndStatus(record.getInstanceURI().toString(), newStatus);
        storage.write(record);
        return record;
    }

    public FormRecord getRecord(int dbId) {
        //this seems silly.
        return storage.read(dbId);
    }

    private void performPurge() {
        if(DeveloperPreferences.isAutoPurgeEnabled()) {
            CaseUtils.purgeCases();
        }
    }

    public void beginBulkSubmit() {
        isBulkProcessing = true;
        isPurgePending = false;
    }

    public void closeBulkSubmit() {
        isBulkProcessing = false;
        if(isPurgePending) {
            performPurge();
        }
        isPurgePending = false;
    }

    /**
     * Performs deep checks on the current form data to establish whether or
     * not the files are in a consistent state. Returns a (human readable)
     * report if not to aid in debugging
     *
     * @param r A Form Record to process
     * @return A tuple whose first argument is a boolean specifying whether the
     * record has passed the verification process.  The second argument is a
     * human readable report for debugging.
     */
    public Pair<Boolean, String> verifyFormRecordIntegrity(FormRecord r) {
        StringBuilder reporter = new StringBuilder();
        try {
            reporter.append("\n").append(r.toString()).append("\n");
            String formPath;
            try {
                //make sure we can retrieve a record. 
                formPath = r.getPath(c);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                reporter.append("ERROR - No file path found for form record. ").append(e.getMessage()).append("\n");
                return new Pair<>(false, reporter.toString());
            }

            //now, make sure there's a file there
            File recordFile = new File(formPath);
            if (!recordFile.exists()) {
                reporter.append("ERROR - No form at file path provided\n");
                return new Pair<>(false, reporter.toString());
            }

            //Give us the info about all of the files in this instance
            reporter.append("\n-File Report-\n");
            File folder = recordFile.getParentFile();
            for (File f : folder.listFiles()) {
                reporter.append(String.format("File:%s \n[Size:%s]\n[LastTouched:%s]\n", f.getName(), String.valueOf(f.length()), new Date(f.lastModified()).toString()));
            }

            reporter.append("\n-Instance Report-\n");
            reporter.append(String.format("Size on Disk:%s\n", String.valueOf(recordFile.length())));

            if (!performLinearFileScan(r, recordFile, false, reporter, "Encrypted Instance File")) {
                return new Pair(false, reporter.toString());
            }

            if (!performLinearFileScan(r, recordFile, true, reporter, "Decrypted Instance File")) {
                return new Pair(false, reporter.toString());
            }

            if (!attemptXmlScan(r, recordFile, reporter)) {
                return new Pair(false, reporter.toString());
            }

            return new Pair(true, reporter.toString());

        } catch (Exception e) {
            return new Pair(false, "Error while preparing attached integrity report: " + e.getMessage() + "\n" + reporter.toString());
        }
    }

    private boolean performLinearFileScan(FormRecord r, File recordFile, boolean useCipher, StringBuilder reporter, String label) {
        //Try to read the actual bytes inline
        InputStream is = null;
        byte[] buffer = new byte[512];
        try {
            //decrypter
            if (useCipher) {
                Cipher decrypter = FormUploadUtil.getDecryptCipher((new SecretKeySpec(r.getAesKey(), "AES")));
                is = new CipherInputStream(new FileInputStream(recordFile), decrypter);
            } else {
                is = new FileInputStream(recordFile);
            }
            long accumulated = 0;
            int read = 0;
            while (read != -1) {
                accumulated += read;
                read = is.read(buffer);
            }

            reporter.append("PASS: Linear scan of ").append(label).append(". ").append(accumulated).append(" bytes read in total\n");
            return true;
        } catch (Exception e) {
            reporter.append("FAILURE: Error during linear scan of ").append(label).append("\n").append(ForceCloseLogger.getStackTrace(e));
            return false;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ioe) {
            }
        }
    }

    private boolean attemptXmlScan(FormRecord r, File recordFile, StringBuilder reporter) {
        KXmlParser parser = new KXmlParser();
        InputStream is = null;
        try {
            Cipher decrypter = FormUploadUtil.getDecryptCipher((new SecretKeySpec(r.getAesKey(), "AES")));
            is = new CipherInputStream(new FileInputStream(recordFile), decrypter);

            parser.setInput(is, "UTF-8");
            parser.setFeature(KXmlParser.FEATURE_PROCESS_NAMESPACES, true);
            while (parser.next() != KXmlParser.END_DOCUMENT) {
                //nothing
            }
            reporter.append("PASS: Instance file reads as valid XML\n");
            return true;
        } catch (Exception e) {
            reporter.append("FAILURE: XML Instance file could not be validated\n").append(ForceCloseLogger.getStackTrace(e));
            return false;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ioe) {
            }
        }
    }
}
