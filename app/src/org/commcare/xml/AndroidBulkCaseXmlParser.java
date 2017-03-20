package org.commcare.xml;

import android.net.ParseException;
import android.net.Uri;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.model.Case;
import org.commcare.engine.references.JavaHttpReference;
import org.commcare.interfaces.HttpRequestEndpoints;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.models.database.user.models.EntityStorageCache;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GlobalConstants;
import org.commcare.xml.bulk.BulkProcessingCaseXmlParser;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.xml.util.InvalidStructureException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * A bulk processing parser for the android platform. Provides superior performance when
 * processing high case loads during syncing and/or processing.
 *
 * @author ctsims
 */
public class AndroidBulkCaseXmlParser extends BulkProcessingCaseXmlParser {
    private HttpRequestEndpoints generator;
    private final EntityStorageCache mEntityCache;
    private final AndroidCaseIndexTable mCaseIndexTable;
    private final SqlStorage<ACase> storage;

    public AndroidBulkCaseXmlParser(KXmlParser parser,
                                         SqlStorage<ACase> storage,
                                         HttpRequestEndpoints generator) {
        this(parser, storage, new EntityStorageCache("case"), new AndroidCaseIndexTable(), generator);
    }

    public AndroidBulkCaseXmlParser(KXmlParser parser,
                                    SqlStorage<ACase> storage,
                                    EntityStorageCache entityStorageCache,
                                    AndroidCaseIndexTable indexTable,
                                    HttpRequestEndpoints generator) {
        super(parser);
        this.generator = generator;
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
        db.beginTransaction();
        ArrayList<Integer> recordIdsToWipe = new ArrayList<>();
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
