package org.commcare.android.cases;

import android.util.Log;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.CaseIndexTable;
import org.commcare.cases.instance.CaseChildElement;
import org.commcare.cases.instance.CaseInstanceTreeElement;
import org.commcare.cases.model.Case;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.utils.CacheHost;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.DataUtil;

import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 */
public class AndroidCaseInstanceTreeElement extends CaseInstanceTreeElement implements CacheHost {
    private static final String TAG = AndroidCaseInstanceTreeElement.class.getSimpleName();
    private final CaseIndexTable mCaseIndexTable;

    private final Hashtable<Integer, Integer> multiplicityIdMapping = new Hashtable<>();

    public AndroidCaseInstanceTreeElement(AbstractTreeElement instanceRoot, SqlStorage<ACase> storage, boolean reportMode) {
        this(instanceRoot, storage, reportMode, new CaseIndexTable());
    }

    public AndroidCaseInstanceTreeElement(AbstractTreeElement instanceRoot, SqlStorage<ACase> storage, boolean reportMode, CaseIndexTable caseIndexTable) {
        super(instanceRoot, storage, reportMode);
        mCaseIndexTable = caseIndexTable;
    }

    @Override
    protected synchronized void getCases() {
        if (cases != null) {
            return;
        }
        objectIdMapping = new Hashtable<>();
        cases = new Vector<>();
        Log.d(TAG, "Getting Cases!");
        long timeInMillis = System.currentTimeMillis();

        int mult = 0;

        for (IStorageIterator i = ((SqlStorage<ACase>) storage).iterate(false); i.hasMore(); ) {
            int id = i.nextID();
            cases.addElement(new CaseChildElement(this, id, null, mult));
            objectIdMapping.put(DataUtil.integer(id), DataUtil.integer(mult));
            multiplicityIdMapping.put(DataUtil.integer(mult), DataUtil.integer(id));
            mult++;
        }
        long value = System.currentTimeMillis() - timeInMillis;
        Log.d(TAG, "Case iterate took: " + value + "ms");
    }

    @Override
    protected Vector<Integer> union(Vector<Integer> selectedCases, Vector<Integer> cases) {
        return DataUtil.union(selectedCases, cases);
    }

    //We're storing this here for now because this is a safe lifecycle object that must represent
    //a single snapshot of the case database, but it could be generalized later.
    private final Hashtable<String, Vector<Integer>> mIndexCache = new Hashtable<>();

    @Override
    protected Vector<Integer> getNextIndexMatch(Vector<String> keys, Vector<Object> values, IStorageUtilityIndexed<?> storage) {
        String firstKey = keys.elementAt(0);

        //If the index object starts with "case_in" it's actually a case index query and we need to run
        //this over the case index table
        if (firstKey.startsWith(Case.INDEX_CASE_INDEX_PRE)) {
            //CTS - March 9, 2015 - Introduced a small cache for child index queries here because they
            //are a frequent target of bulk operations like graphing which do multiple requests across the 
            //same query.
            //TODO: This should likely be generalized for a number of other queries with bulk/nodeset
            //returns
            String indexName = firstKey.substring(Case.INDEX_CASE_INDEX_PRE.length());
            String value = (String) values.elementAt(0);

            //TODO: Evaluate whether our indices could contain "|" but I don't imagine how they could.
            String indexCacheKey = firstKey + "|" + value;

            //Check whether we've got a cache of this index.
            if (mIndexCache.containsKey(indexCacheKey)) {
                //remove the match from the inputs
                keys.removeElementAt(0);
                values.removeElementAt(0);
                return mIndexCache.get(indexCacheKey);
            }

            Vector<Integer> matchingCases = mCaseIndexTable.getCasesMatchingIndex(indexName, value);

            //Clear the most recent index and wipe it, because there is no way it is going to be useful
            //after this
            mMostRecentBatchFetch = new String[2][];

            //remove the match from the inputs
            keys.removeElementAt(0);
            values.removeElementAt(0);

            //For now we're only going to run this on very small data sets because we don't
            //want to manage this too explicitly until we generalize. Almost all results here
            //will be very very small either way (~O(10's of cases)), so given that this only
            //exists across one session that won't get out of hand
            if (matchingCases.size() < 50) {
                //Should never hit this, but don't wanna have any runaway memory if we do.
                if (mIndexCache.size() > 100) {
                    mIndexCache.clear();
                }

                mIndexCache.put(indexCacheKey, matchingCases);
            }
            return matchingCases;
        }

        //Otherwise see how many of these we can bulk process
        int numKeys;
        for (numKeys = 0; numKeys < keys.size(); ++numKeys) {
            //If the current key is an index fetch, we actually can't do it in bulk,
            //so we need to stop
            if (keys.elementAt(numKeys).startsWith(Case.INDEX_CASE_INDEX_PRE)) {
                break;
            }
            //otherwise, it's now in our queue
        }

        SqlStorage<ACase> sqlStorage = ((SqlStorage<ACase>) storage);
        String[] namesToMatch = new String[numKeys];
        String[] valuesToMatch = new String[numKeys];
        for (int i = numKeys - 1; i >= 0; i--) {
            namesToMatch[i] = keys.elementAt(i);
            valuesToMatch[i] = (String) values.elementAt(i);
        }
        mMostRecentBatchFetch = new String[2][];
        mMostRecentBatchFetch[0] = namesToMatch;
        mMostRecentBatchFetch[1] = valuesToMatch;

        Vector<Integer> ids = sqlStorage.getIDsForValues(namesToMatch, valuesToMatch);

        //Ok, we matched! Remove all of the keys that we matched
        for (int i = 0; i < numKeys; ++i) {
            keys.removeElementAt(0);
            values.removeElementAt(0);
        }
        return ids;
    }

    public String getCacheIndex(TreeReference ref) {
        //NOTE: there's no evaluation here as to whether the ref is suitable
        //we only follow one pattern for now and it's evaluated below. 

        getCases();

        //Testing - Don't bother actually seeing whether this fits
        int i = ref.getMultiplicity(1);
        if (i != -1) {
            Integer val = this.multiplicityIdMapping.get(DataUtil.integer(i));
            if (val == null) {
                return null;
            } else {
                return val.toString();
            }
        }
        return null;
    }

    @Override
    public boolean isReferencePatternCachable(TreeReference ref) {
        //we only support one pattern here, a raw, qualified
        //reference to an element at the case level with no
        //predicate support. The ref basically has to be a raw
        //pointer to one of this instance's children
        if (!ref.isAbsolute()) {
            return false;
        }

        if (ref.hasPredicates()) {
            return false;
        }
        if (ref.size() != 2) {
            return false;
        }

        if (!"casedb".equalsIgnoreCase(ref.getName(0))) {
            return false;
        }
        if (!"case".equalsIgnoreCase(ref.getName(1))) {
            return false;
        }
        return ref.getMultiplicity(1) >= 0;

    }

    private String[][] mMostRecentBatchFetch = null;

    @Override
    public String[][] getCachePrimeGuess() {
        return mMostRecentBatchFetch;
    }

}
