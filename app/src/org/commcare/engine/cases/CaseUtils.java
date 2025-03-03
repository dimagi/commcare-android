package org.commcare.engine.cases;

import static org.commcare.cases.model.Case.INDEX_CASE_ID;
import static org.commcare.cases.util.CaseDBUtils.xordata;

import com.google.common.collect.ImmutableSet;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.ledger.LedgerPurgeFilter;
import org.commcare.cases.model.Case;
import org.commcare.cases.model.CaseIndex;
import org.commcare.cases.util.CasePurgeFilter;
import org.commcare.cases.util.InvalidCaseGraphException;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.SqlStorageIterator;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.modern.util.Pair;
import org.commcare.util.LogTypes;
import org.commcare.utils.CommCareUtil;
import org.javarosa.core.model.User;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.util.DAG;
import org.javarosa.core.util.MD5;
import org.javarosa.model.xform.XPathReference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/**
 * Utilities for performing complex operations on the case database.
 *
 * Created by ctsims on 2/25/2016.
 */
public class CaseUtils {

    public static String computeCaseDbHash(SqlStorage<?> storage) {
        long timeStart = System.currentTimeMillis();

        byte[] data = new byte[MD5.length];
        for (int i = 0; i < data.length; ++i) {
            data[i] = 0;
        }
        boolean casesExist = false;
        long count = 0;

        for (SqlStorageIterator i = storage.iterate(false, new String[]{INDEX_CASE_ID}); i.hasMore(); ) {
            byte[] current = MD5.hash(i.peekIncludedMetadata(INDEX_CASE_ID).getBytes());
            data = xordata(data, current);
            casesExist = true;
            count++;
            i.nextID();
        }

        //In the base case (with no cases), the case hash is empty
        if (!casesExist) {
            return "";
        }

        long timeEnd = System.currentTimeMillis();
        Logger.log(LogTypes.TYPE_MAINTENANCE, String.format(
                "Hashed %d Cases records in %dms",
                count, timeEnd-timeStart));

        return MD5.toHex(data);
    }
    /**
     * Perform a case purge against the logged in user with the logged in app in local storage.
     *
     * Will fail if the app is not ready for DB operations at the user level.
     */
    public static void purgeCases() throws InvalidCaseGraphException {
        long start = System.currentTimeMillis();
        //We need to determine if we're using ownership for purging. For right now, only in sync mode
        Vector<String> owners = getAllOwners();
        SQLiteDatabase db = CommCareApplication.instance().getUserDbHandle();
        int removedCaseCount;
        int removedLedgers;
        db.beginTransaction();
        try {
            SqlStorage<ACase> storage = CommCareApplication.instance().getUserStorage(ACase.STORAGE_KEY, ACase.class);
            DAG<String, int[], String> fullCaseGraph = getFullCaseGraph(storage, new AndroidCaseIndexTable(), owners);

            CasePurgeFilter filter = new CasePurgeFilter(fullCaseGraph);
            if (filter.invalidEdgesWereRemoved()) {
                Logger.log(LogTypes.SOFT_ASSERT, "An invalid edge was created in the internal " +
                        "case DAG of a case purge filter, meaning that at least 1 case on the " +
                         "device had an index into another case that no longer exists on the device");
                Logger.log(LogTypes.TYPE_ERROR_ASSERTION, "Case lists on the server and device" +
                        " were out of sync. The following cases were expected to be on the device, " +
                        "but were missing: " + filter.getMissingCasesString() + ". As a result, the " +
                        "following cases were also removed from the device: " + filter.getRemovedCasesString());
            }

            Vector<Integer> casesRemoved = storage.removeAll(filter.getCasesToRemove());
            removedCaseCount = casesRemoved.size();

            AndroidCaseIndexTable indexTable = new AndroidCaseIndexTable(db);
            for (int recordId : casesRemoved) {
                indexTable.clearCaseIndices(recordId);
            }


            SqlStorage<Ledger> stockStorage = CommCareApplication.instance().getUserStorage(Ledger.STORAGE_KEY, Ledger.class);
            LedgerPurgeFilter stockFilter = new LedgerPurgeFilter(stockStorage, storage);
            removedLedgers = stockStorage.removeAll(stockFilter).size();
            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }

        long taken = System.currentTimeMillis() - start;

        Logger.log(LogTypes.TYPE_MAINTENANCE, String.format(
                "Purged [%d Case, %d Ledger] records in %dms",
                removedCaseCount, removedLedgers, taken));

    }

    private static Vector<String> getAllOwners() {
        Vector<String> users = new Vector<>();
        Vector<String> owners = new Vector<>();
        SqlStorage<User> userStorage = CommCareApplication.instance().getUserStorage(User.STORAGE_KEY, User.class);
        for (IStorageIterator<User> userIterator = userStorage.iterate(); userIterator.hasMore(); ) {
            String id = userIterator.nextRecord().getUniqueId();
            owners.addElement(id);
            users.addElement(id);
        }

        //Now add all of the relevant groups
        //TODO: Wow. This is.... kind of megasketch
        for (String userId : users) {
            DataInstance instance = CommCareUtil.loadFixture("user-groups", userId);
            if (instance == null) {
                continue;
            }
            EvaluationContext ec = new EvaluationContext(instance);
            for (TreeReference ref : ec.expandReference(
                    XPathReference.getPathExpr("/groups/group/@id").getReference())) {
                AbstractTreeElement<AbstractTreeElement> idelement = ec.resolveReference(ref);
                if (idelement.getValue() != null) {
                    owners.addElement(idelement.getValue().uncast().getString());
                }
            }
        }
        return owners;
    }

    /**
     * Get records ids for all related cases for the given set of cases including the original set of cases
     *
     * @param recordIds databse ids for the cases we want to find related cases
     * @return database ids for all related cases for the given set of cases
     */
    public static Vector<Integer> getRelatedCases(Set<String> recordIds) {
        if (recordIds.isEmpty()) {
            return new Vector<>();
        }

        SqlStorage<ACase> storage = CommCareApplication.instance().getUserStorage(ACase.STORAGE_KEY, ACase.class);
        DAG<String, int[], String> fullCaseGraph = getFullCaseGraph(storage, new AndroidCaseIndexTable(),
                new Vector<>());
        Set<String> caseIds = getCaseIdsFromRecordsIds(storage, recordIds);
        Set<String> relatedCaseIds = fullCaseGraph.getRelatedRecords(caseIds);
        return storage.getIDsForValues(new String[]{INDEX_CASE_ID}, relatedCaseIds.toArray());
    }

    private static Set<String> getCaseIdsFromRecordsIds(SqlStorage<ACase> storage, Set<String> recordIds) {
        Set<String> caseIds = new HashSet<>(recordIds.size());
        for (String recordId : recordIds) {
            String[] result = storage.getMetaDataForRecord(Integer.parseInt(recordId),
                    new String[]{INDEX_CASE_ID});
            caseIds.add(result[0]);
        }
        return caseIds;
    }

    public static DAG<String, int[], String> getFullCaseGraph(SqlStorage<ACase> caseStorage,
            AndroidCaseIndexTable indexTable,
            Vector<String> owners) {
        DAG<String, int[], String> caseGraph = new DAG<>();
        Vector<Pair<String, String>> indexHolder = new Vector<>();

        HashMap<Integer, Vector<Pair<String, String>>> caseIndexMap = indexTable.getCaseIndexMap();

        // Pass 1: Create a DAG which contains all of the cases on the phone as nodes, and has a
        // directed edge for each index (from the 'child' case pointing to the 'parent' case) with
        // the appropriate relationship tagged
        for (SqlStorageIterator<ACase> i = caseStorage.iterate(false, new String[]{
                Case.INDEX_OWNER_ID, Case.INDEX_CASE_STATUS, INDEX_CASE_ID}); i.hasMore(); ) {

            String ownerId = i.peekIncludedMetadata(Case.INDEX_OWNER_ID);
            boolean closed = i.peekIncludedMetadata(Case.INDEX_CASE_STATUS).equals("closed");
            String caseID = i.peekIncludedMetadata(INDEX_CASE_ID);
            int caseRecordId = i.nextID();


            boolean owned = true;
            if (owners != null) {
                owned = owners.contains(ownerId);
            }

            Vector<Pair<String, String>> indices = caseIndexMap.get(caseRecordId);

            if (indices != null) {
                // In order to deal with multiple indices pointing to the same case with different
                // relationships, we'll need to traverse once to eliminate any ambiguity
                for (Pair<String, String> index : indices) {
                    Pair<String, String> toReplace = null;
                    boolean skip = false;
                    for (Pair<String, String> existing : indexHolder) {
                        if (existing.first.equals(index.first)) {
                            if (existing.second.equals(CaseIndex.RELATIONSHIP_EXTENSION) && !index.second.equals(CaseIndex.RELATIONSHIP_EXTENSION)) {
                                toReplace = existing;
                            } else {
                                skip = true;
                            }
                            break;
                        }
                    }
                    if (toReplace != null) {
                        indexHolder.removeElement(toReplace);
                    }
                    if (!skip) {
                        indexHolder.addElement(index);
                    }
                }
            }
            int nodeStatus = 0;
            if (owned) {
                nodeStatus |= CasePurgeFilter.STATUS_OWNED;
            }

            if (!closed) {
                nodeStatus |= CasePurgeFilter.STATUS_OPEN;
            }

            if (owned && !closed) {
                nodeStatus |= CasePurgeFilter.STATUS_RELEVANT;
            }

            caseGraph.addNode(caseID, new int[]{nodeStatus, caseRecordId});

            for (Pair<String, String> index : indexHolder) {
                caseGraph.setEdge(caseID, index.first, index.second);
            }
            indexHolder.removeAllElements();
        }

        return caseGraph;
    }
}
