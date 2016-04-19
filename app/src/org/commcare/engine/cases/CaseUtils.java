package org.commcare.engine.cases;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.ledger.LedgerPurgeFilter;
import org.commcare.cases.util.CasePurgeFilter;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.UserStorageClosedException;
import org.commcare.android.database.user.models.ACase;
import org.commcare.models.database.user.models.CaseIndexTable;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.model.xform.XPathReference;

import java.util.Vector;

/**
 * Utilities for performing complex operations on the case database.
 *
 * Created by ctsims on 2/25/2016.
 */
public class CaseUtils {
    /**
     * Perform a case purge against the logged in user with the logged in app in local storage.
     *
     * Will fail if the app is not ready for DB operations at the user level.
     */
    public static void purgeCases() {
        long start = System.currentTimeMillis();
        //We need to determine if we're using ownership for purging. For right now, only in sync mode
        Vector<String> owners = new Vector<>();
        Vector<String> users = new Vector<>();
        for (IStorageIterator<User> userIterator = CommCareApplication._()
                .getUserStorage(User.STORAGE_KEY, User.class).iterate(); userIterator.hasMore(); ) {
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
            for (TreeReference ref : ec.expandReference(XPathReference.getPathExpr("/groups/group/@id").getReference())) {
                AbstractTreeElement<AbstractTreeElement> idelement = ec.resolveReference(ref);
                if (idelement.getValue() != null) {
                    owners.addElement(idelement.getValue().uncast().getString());
                }
            }
        }

        SQLiteDatabase db;
        try {
            db = CommCareApplication._().getUserDbHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        db.beginTransaction();
        int removedCaseCount;
        int removedLedgers;
        try {
            SqlStorage<ACase> storage = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class);

            CasePurgeFilter filter = new CasePurgeFilter(storage, owners);
            if (filter.invalidEdgesWereRemoved()) {
                Logger.log(AndroidLogger.SOFT_ASSERT, "An invalid edge was created in the internal " +
                        "case DAG of a case purge filter, meaning that at least 1 case on the " +
                        "device had an index into another case that no longer exists on the device");
                Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Case lists on the server and device" +
                        " were out of sync. The following cases were expected to be on the device, " +
                        "but were missing: " + filter.getMissingCasesString() + ". As a result, the " +
                        "following cases were also removed from the device: " + filter.getRemovedCasesString());
            }

            Vector<Integer> casesRemoved = storage.removeAll(filter);
            removedCaseCount = casesRemoved.size();
            CaseIndexTable indexTable = new CaseIndexTable(db);
            for (int recordId : casesRemoved) {
                indexTable.clearCaseIndices(recordId);
            }


            SqlStorage<Ledger> stockStorage = CommCareApplication._().getUserStorage(Ledger.STORAGE_KEY, Ledger.class);
            LedgerPurgeFilter stockFilter = new LedgerPurgeFilter(stockStorage, storage);
            removedLedgers = stockStorage.removeAll(stockFilter).size();
            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }

        long taken = System.currentTimeMillis() - start;

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, String.format(
                "Purged [%d Case, %d Ledger] records in %dms",
                removedCaseCount, removedLedgers, taken));

    }
}
