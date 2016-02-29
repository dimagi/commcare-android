package org.commcare.android.tasks;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.util.Pair;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.logging.AndroidLogger;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
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
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

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

/**
 * @author ctsims
 */
public abstract class FormRecordCleanupTask<R> extends CommCareTask<Void, Integer, Integer, R> {
    private static final String TAG = FormRecordCleanupTask.class.getSimpleName();

    private final Context context;
    private final CommCarePlatform platform;

    public static final int STATUS_CLEANUP = -1;
    private static final int SUCCESS = -1;

    public FormRecordCleanupTask(Context context, CommCarePlatform platform, int taskId) {
        this.context = context;
        this.platform = platform;
        this.taskId = taskId;
    }

    @Override
    protected Integer doTaskBackground(Void... params) {
        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        String currentAppId = CommCareApplication._().getCurrentApp().getAppRecord().getApplicationId();

        Vector<Integer> recordsToRemove = storage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new String[]{FormRecord.STATUS_SAVED, currentAppId});
        int numOldRecordsRemoved = recordsToRemove.size();

        Vector<Integer> unindexedRecords =
                storage.getIDsForValues(new String[]{FormRecord.META_STATUS}, new String[]{FormRecord.STATUS_UNINDEXED});
        int count = 0;
        for (int recordID : unindexedRecords) {
            FormRecord r = storage.read(recordID);

            try {
                updateAndWriteUnindexedRecordTo(context, platform, r, storage, FormRecord.STATUS_SAVED);
            } catch (FileNotFoundException | InvalidStructureException e) {
                // No form or bad form data, mark for deletion
                recordsToRemove.add(recordID);
            } catch (XmlPullParserException | IOException |
                    UnfullfilledRequirementsException e) {
                // Not really sure what happened; just skip
            }

            count++;
            this.publishProgress(count, unindexedRecords.size());
        }

        this.publishProgress(STATUS_CLEANUP);

        SqlStorage<SessionStateDescriptor> ssdStorage =
                CommCareApplication._().getUserStorage(SessionStateDescriptor.class);

        for (int recordID : recordsToRemove) {
            //We don't know anything about the session yet, so give it -1 to flag that
            wipeRecord(context, -1, recordID, storage, ssdStorage);
        }

        int totalRecordsRemoved = recordsToRemove.size();
        Log.d(TAG, "Synced: " + unindexedRecords.size() +
                ". Removed: " + numOldRecordsRemoved + " old records, and " +
                (totalRecordsRemoved - numOldRecordsRemoved) + " busted new ones");
        return SUCCESS;
    }

    /**
     * Reparse the saved form instance associated with the form record and
     * apply any updates found to the form record, such as UUID and date
     * modified, returning an updated copy with the status set to saved.  Write
     * the updated record to storage.
     *
     * @param context   Used to get the filepath of the form instance
     *                  associated with the record.
     * @param oldRecord Reparse this record and return an updated copy of it
     * @param storage   User storage where updated FormRecord is written
     * @return The reparsed form record and the associated case id, if present
     * @throws IOException                       Problem opening the saved form
     *                                           attached to the record.
     * @throws InvalidStructureException         Occurs during reparsing of the
     *                                           form attached to record.
     * @throws XmlPullParserException
     * @throws UnfullfilledRequirementsException Parsing encountered a platform
     *                                           versioning problem
     */
    public static FormRecord updateAndWriteRecord(Context context,
                                                  FormRecord oldRecord,
                                                  SqlStorage<FormRecord> storage)
            throws InvalidStructureException, IOException,
            XmlPullParserException, UnfullfilledRequirementsException {

        Pair<FormRecord, String> recordUpdates = reparseRecord(context, oldRecord);

        FormRecord updated = recordUpdates.first;
        String caseId = recordUpdates.second;

        if (caseId != null &&
                FormRecord.STATUS_UNINDEXED.equals(oldRecord.getStatus())) {
            throw new RuntimeException("Trying to update an unindexed record without performing the indexing");
        }

        storage.write(updated);
        return updated;
    }

    /**
     * Reparse the saved form instance associated with the form record and
     * apply any updates found to the form record, such as UUID and date
     * modified, returning an updated copy with the status set to saved.  Write
     * the updated record to storage. If the record is unindexed and associated
     * with a case id, recompute and write the SessionStateDescriptor too.
     *
     * @param context   Used to get the filepath of the form instance
     *                  associated with the record.
     * @param platform  Used to generate SessionStateDescriptor for instances
     *                  that reference a case.
     * @param oldRecord Reparse this record and return an updated copy of it
     * @param storage   User storage where updated FormRecord is written
     * @throws IOException                       Problem opening the saved form
     *                                           attached to the record.
     * @throws InvalidStructureException         Occurs during reparsing of the
     *                                           form attached to record.
     * @throws XmlPullParserException
     * @throws UnfullfilledRequirementsException Parsing encountered a platform
     *                                           versioning problem
     */
    public static void updateAndWriteUnindexedRecordTo(Context context,
                                                       CommCarePlatform platform,
                                                       FormRecord oldRecord,
                                                       SqlStorage<FormRecord> storage,
                                                       String saveStatus)
            throws InvalidStructureException, IOException,
            XmlPullParserException, UnfullfilledRequirementsException {

        Pair<FormRecord, String> recordUpdates = reparseRecord(context, oldRecord);

        FormRecord updated = recordUpdates.first;
        updated = updated.updateInstanceAndStatus(updated.getInstanceURI().toString(), saveStatus);
        String caseId = recordUpdates.second;

        if (caseId != null &&
                FormRecord.STATUS_UNINDEXED.equals(oldRecord.getStatus())) {
            // There is a case id associated with an unidexed form record,
            // calculate the state descripter and write it.
            // Occurs when loading forms manually onto the device using DataPullTask.
            AndroidSessionWrapper asw
                    = AndroidSessionWrapper.mockEasiestRoute(platform,
                    oldRecord.getFormNamespace(), caseId);
            asw.setFormRecordId(updated.getID());

            SqlStorage<SessionStateDescriptor> ssdStorage =
                    CommCareApplication._().getUserStorage(SessionStateDescriptor.class);

            ssdStorage.write(asw.getSessionStateDescriptor());
        }

        storage.write(updated);
    }

    /**
     * Reparse the saved form instance associated with the form record and
     * apply any updates found to the form record, such as UUID and date
     * modified, returning an updated copy with status set to saved.
     *
     * @param context Used to get the filepath of the form instance
     *                associated with the record.
     * @param r       Reparse this record and return an updated copy of it
     * @return The reparsed form record and the associated case id, if present
     * @throws IOException                       Problem opening the saved form
     *                                           attached to the record.
     * @throws InvalidStructureException         Occurs during reparsing of the
     *                                           form attached to record.
     * @throws XmlPullParserException
     * @throws UnfullfilledRequirementsException Parsing encountered a platform
     *                                           versioning problem
     */
    private static Pair<FormRecord, String> reparseRecord(Context context,
                                                          FormRecord r)
            throws IOException, InvalidStructureException,
            XmlPullParserException, UnfullfilledRequirementsException {
        final String[] caseIDs = new String[1];
        final Date[] modified = new Date[]{new Date(0)};
        final String[] uuid = new String[1];

        // NOTE: This does _not_ parse and process the case data. It's only for
        // getting meta information about the entry session.
        TransactionParserFactory factory = new TransactionParserFactory() {
            @Override
            public TransactionParser getParser(KXmlParser parser) {
                String name = parser.getName();
                if ("case".equals(name)) {
                    return buildCaseParser(parser.getNamespace(), parser, caseIDs);
                } else if ("meta".equalsIgnoreCase(name)) {
                    return buildMetaParser(uuid, modified, parser);
                }
                return null;
            }
        };

        String path = r.getPath(context);
        InputStream is = null;
        FileInputStream fis = new FileInputStream(path);
        try {
            Cipher decrypter = Cipher.getInstance("AES");
            decrypter.init(Cipher.DECRYPT_MODE, new SecretKeySpec(r.getAesKey(), "AES"));
            is = new CipherInputStream(fis, decrypter);

            // Construct parser for this form's internal data.
            DataModelPullParser parser = new DataModelPullParser(is, factory);

            // populate uuid, modified, and caseIDs arrays by parsing
            parser.parse();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException("No Algorithm while attempting to decode form submission for processing");
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            throw new RuntimeException("Invalid cipher data while attempting to decode form submission for processing");
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeException("Invalid Key Data while attempting to decode form submission for processing");
        } finally {
            fis.close();
            if (is != null) {
                is.close();
            }
        }

        // TODO: We should be committing all changes to form record models via the ASW objects,
        // not manually.
        FormRecord parsed = new FormRecord(r.getInstanceURI().toString(),
                r.getStatus(), r.getFormNamespace(), r.getAesKey(),
                uuid[0], modified[0], r.getAppId());
        parsed.setID(r.getID());

        // Make sure that the instance is no longer editable
        ContentValues cv = new ContentValues();
        cv.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, Boolean.toString(false));
        context.getContentResolver().update(r.getInstanceURI(), cv, null, null);

        return new Pair<>(parsed, caseIDs[0]);
    }

    public static void wipeRecord(Context c, SessionStateDescriptor existing) {
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

    private static void wipeRecord(Context c, int sessionId, int formRecordId) {
        wipeRecord(c, sessionId, formRecordId,
                CommCareApplication._().getUserStorage(FormRecord.class),
                CommCareApplication._().getUserStorage(SessionStateDescriptor.class));
    }

    /**
     * Remove form record and associated session state descriptor from storage
     * and delete form instance files linked to the form record.
     */
    public static void wipeRecord(Context context, int sessionId,
                                  int formRecordId,
                                  SqlStorage<FormRecord> frStorage,
                                  SqlStorage<SessionStateDescriptor> ssdStorage) {
        if (sessionId != -1) {
            try {
                SessionStateDescriptor ssd = ssdStorage.read(sessionId);

                int ssdFrid = ssd.getFormRecordId();
                if (formRecordId == -1) {
                    formRecordId = ssdFrid;
                } else if (formRecordId != ssdFrid) {
                    //Not good.
                    Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                            "Inconsistent formRecordId's in session storage");
                }
            } catch (Exception e) {
                Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                        "Session ID exists, but with no record (or broken record)");
            }
        }

        if (formRecordId != -1) {
            try {
                FormRecord r = frStorage.read(formRecordId);
                removeInstanceFile(context, r);

                //See if there is a hanging session ID for this
                if (sessionId == -1) {
                    sessionId = loadSSDIDFromFormRecord(ssdStorage, formRecordId);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                        "Session ID exists, but with no record (or broken record)");
            }
        }

        //Delete 'em if you got 'em
        if (sessionId != -1) {
            ssdStorage.remove(sessionId);
        }
        if (formRecordId != -1) {
            frStorage.remove(formRecordId);
        }
    }

    private static void removeInstanceFile(Context context, FormRecord record) {
        String dataPath;
        try {
            dataPath = record.getPath(context);
        } catch (FileNotFoundException e) {
            // FormRecords won't have instance uris if the form was never started
            return;
        }

        if (dataPath != null) {
            String selection = InstanceColumns.INSTANCE_FILE_PATH + "=?";
            Cursor c = context.getContentResolver().query(InstanceColumns.CONTENT_URI, new String[]{InstanceColumns._ID}, selection, new String[]{dataPath}, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        //There's a cursor for this file, good.
                        long id = c.getLong(0);

                        //this should take care of the files
                        context.getContentResolver().delete(ContentUris.withAppendedId(InstanceColumns.CONTENT_URI, id), null, null);
                    } else {
                        //No instance record for whatever reason, manually wipe files
                        FileUtil.deleteFileOrDir(dataPath);
                    }
                } finally {
                    c.close();
                }
            }
        }
    }

    private static int loadSSDIDFromFormRecord(SqlStorage<SessionStateDescriptor> ssdStorage,
                                            int formRecordId) {
        Vector<Integer> sessionIds =
                ssdStorage.getIDsForValue(SessionStateDescriptor.META_FORM_RECORD_ID, formRecordId);
        // We really shouldn't be able to end up with sessionId's
        // that point to more than one thing.
        if (sessionIds.isEmpty()) {
            return -1;
        } else if (sessionIds.size() > 1) {
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                    "Multiple session ID's pointing to the same form record");

        }
        return sessionIds.firstElement();
    }

    private static TransactionParser buildCaseParser(String namespace,
                                                     KXmlParser parser,
                                                     final String[] caseIDs) {
        //If we have a proper 2.0 namespace, good.
        if (CaseXmlParser.CASE_XML_NAMESPACE.equals(namespace)) {
            return new AndroidCaseXmlParser(parser, CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class)) {
                @Override
                public void commit(Case parsed) throws IOException {
                    String incoming = parsed.getCaseId();
                    if (incoming != null && !"".equals(incoming)) {
                        caseIDs[0] = incoming;
                    }
                }

                @Override
                public ACase retrieve(String entityId) {
                    caseIDs[0] = entityId;
                    ACase c = new ACase("", "");
                    c.setCaseId(entityId);
                    return c;
                }
            };
        } else {
            // Otherwise, this gets more tricky. Ideally we'd want to
            // skip this block for compatibility purposes, but we can
            // at least try to get a caseID (which is all we want)
            return new BestEffortBlockParser(parser, null, null, new String[]{"case_id"}) {
                @Override
                public void commit(Hashtable<String, String> values) {
                    if (values.containsKey("case_id")) {
                        caseIDs[0] = values.get("case_id");
                    }
                }
            };
        }
    }

    private static TransactionParser buildMetaParser(final String[] uuid,
                                                     final Date[] modified,
                                                     KXmlParser parser) {
        return new MetaDataXmlParser(parser) {
            @Override
            public void commit(String[] meta) throws IOException {
                if (meta[0] != null) {
                    modified[0] = DateUtils.parseDateTime(meta[0]);
                }
                uuid[0] = meta[1];
            }
        };
    }


}
