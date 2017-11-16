package org.commcare.xml;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.model.Case;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.models.database.user.models.EntityStorageCache;
import org.commcare.xml.bulk.BulkProcessingCaseXmlParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A bulk processing parser for the android platform. Provides superior performance when
 * processing high case loads during syncing and/or processing.
 *
 * @author ctsims
 */
public class AndroidBulkCaseXmlParser extends BulkProcessingCaseXmlParser {
    private final EntityStorageCache mEntityCache;
    private final AndroidCaseIndexTable mCaseIndexTable;
    private final SqlStorage<ACase> storage;

    public AndroidBulkCaseXmlParser(KXmlParser parser,
                                    SqlStorage<ACase> storage) {
        this(parser, storage, new EntityStorageCache("case"), new AndroidCaseIndexTable());
    }

    public AndroidBulkCaseXmlParser(KXmlParser parser,
                                    SqlStorage<ACase> storage,
                                    EntityStorageCache entityStorageCache,
                                    AndroidCaseIndexTable indexTable) {
        super(parser);
        mEntityCache = entityStorageCache;
        mCaseIndexTable = indexTable;
        this.storage = storage;
    }

    protected SQLiteDatabase getDbHandle() {
        return CommCareApplication.instance().getUserDbHandle();
    }

    @Override
    protected Case buildCase(String name, String typeId) {
        return new ACase(name, typeId);
    }

    @Override
    protected void performBulkRead(Set<String> currentBulkReadSet, Map<String, Case> currentOperatingSet) throws InvalidStructureException, IOException, XmlPullParserException {
        SQLiteDatabase db;
        db = getDbHandle();
        db.beginTransaction();
        try {
            for (ACase c : storage.getBulkRecordsForIndex(Case.INDEX_CASE_ID, currentBulkReadSet)) {
                currentOperatingSet.put(c.getCaseId(), c);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    protected void performBulkWrite(LinkedHashMap<String, Case> writeLog) throws IOException {
        SQLiteDatabase db;
        db = getDbHandle();
        ArrayList<Integer> recordIdsToWipe = new ArrayList<>();

        db.beginTransaction();
        try {
            for (String cid : writeLog.keySet()) {
                Case c = writeLog.get(cid);
                storage.write(c);
                recordIdsToWipe.add(c.getID());
            }
            mEntityCache.invalidateCaches(recordIdsToWipe);
            mCaseIndexTable.clearCaseIndices(recordIdsToWipe);

            for (String cid : writeLog.keySet()) {
                Case c = writeLog.get(cid);
                mCaseIndexTable.indexCase(c);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

    }
}
