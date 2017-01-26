package org.commcare.engine.cases;

import android.util.Log;

import org.commcare.cases.instance.CaseInstanceTreeElement;
import org.commcare.cases.model.Case;
import org.commcare.cases.util.CaseModelFetchCue;
import org.commcare.cases.query.IndexedSetMemberLookup;
import org.commcare.cases.query.IndexedValueLookup;
import org.commcare.cases.query.PredicateProfile;
import org.commcare.cases.query.QueryPlanner;
import org.commcare.models.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.models.database.user.models.CaseIndexTable;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.trace.EvaluationTrace;
import org.javarosa.core.model.utils.CacheHost;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.DataUtil;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 */
public class AndroidCaseInstanceTreeElement extends CaseInstanceTreeElement implements CacheHost {
    private static final String TAG = AndroidCaseInstanceTreeElement.class.getSimpleName();

    private final CaseIndexTable mCaseIndexTable;

    private final Hashtable<Integer, Integer> multiplicityIdMapping = new Hashtable<>();

    //We're storing this here for now because this is a safe lifecycle object that must represent
    //a single snapshot of the case database, but it could be generalized later.
    private Hashtable<String, Vector<Integer>> mIndexCache = new Hashtable<>();

    //TODO: Document
    private Hashtable<String, Vector<Integer>> mPairedIndexCache = new Hashtable<>();

    private String[][] mMostRecentBatchFetch = null;

    public AndroidCaseInstanceTreeElement(AbstractTreeElement instanceRoot, SqlStorage<ACase> storage) {
        this(instanceRoot, storage, new CaseIndexTable());
    }

    public AndroidCaseInstanceTreeElement(AbstractTreeElement instanceRoot, SqlStorage<ACase> storage,
                                          CaseIndexTable caseIndexTable) {
        super(instanceRoot, storage);
        mCaseIndexTable = caseIndexTable;
    }

    @Override
    protected void initBasicQueryHandlers(QueryPlanner queryPlanner) {
        super.initBasicQueryHandlers(queryPlanner);
        queryPlanner.addQueryHandler(new CaseIndexPrefetchHandler(mCaseIndexTable));
    }



    @Override
    protected synchronized void loadElements() {
        if (elements != null) {
            return;
        }
        objectIdMapping = new Hashtable<>();
        elements = new Vector<>();
        Log.d(TAG, "Getting Cases!");
        long timeInMillis = System.currentTimeMillis();

        int mult = 0;

        for (IStorageIterator i = ((SqlStorage<ACase>)storage).iterate(false); i.hasMore(); ) {
            int id = i.nextID();
            elements.add(buildElement(this, id, null, mult));
            objectIdMapping.put(DataUtil.integer(id), DataUtil.integer(mult));
            multiplicityIdMapping.put(DataUtil.integer(mult), DataUtil.integer(id));
            mult++;
        }
        long value = System.currentTimeMillis() - timeInMillis;
        Log.d(TAG, "Case iterate took: " + value + "ms");
    }

    @Override
    protected Vector<Integer> getNextIndexMatch(Vector<PredicateProfile> profiles,
                                                IStorageUtilityIndexed<?> storage) {
        //If the index object starts with "case-in-" it's actually a case index query and we need to run
        //this over the case index table
        String firstKey = profiles.elementAt(0).getKey();
        if (firstKey.startsWith(Case.INDEX_CASE_INDEX_PRE)) {
            return performCaseIndexQuery(firstKey, profiles);
        }

        //Otherwise see how many of these we can bulk process
        int numKeys;
        for (numKeys = 0; numKeys < profiles.size(); ++numKeys) {
            //If the current key is an index fetch, we actually can't do it in bulk,
            //so we need to stop
            if (profiles.elementAt(numKeys).getKey().startsWith(Case.INDEX_CASE_INDEX_PRE) ||
                    !(profiles.elementAt(numKeys) instanceof IndexedValueLookup)) {
                break;
            }
            //otherwise, it's now in our queue
        }

        SqlStorage<ACase> sqlStorage = ((SqlStorage<ACase>)storage);

        String[] namesToMatch = new String[numKeys];
        String[] valuesToMatch = new String[numKeys];

        String cacheKey = "";
        String keyDescription ="";

        for (int i = numKeys - 1; i >= 0; i--) {
            namesToMatch[i] = profiles.elementAt(i).getKey();
            valuesToMatch[i] = (String)
                    (((IndexedValueLookup)profiles.elementAt(i)).value);

            cacheKey += "|" + namesToMatch[i] + "=" + valuesToMatch[i];
            keyDescription = namesToMatch[i] + "|";
        }
        mMostRecentBatchFetch = new String[2][];
        mMostRecentBatchFetch[0] = namesToMatch;
        mMostRecentBatchFetch[1] = valuesToMatch;

        Vector<Integer> ids;
        if(mPairedIndexCache.containsKey(cacheKey)) {
            ids = mPairedIndexCache.get(cacheKey);
        } else {
            EvaluationTrace trace = new EvaluationTrace("Case Storage Lookup" + "["+keyDescription + "]");
            ids = sqlStorage.getIDsForValues(namesToMatch, valuesToMatch);

            trace.setOutcome("Results: " + ids.size());
            queryPlanner.reportTrace(trace);


            mPairedIndexCache.put(cacheKey, ids);
        }

        //Ok, we matched! Remove all of the keys that we matched
        for (int i = 0; i < numKeys; ++i) {
            profiles.removeElementAt(0);
        }
        return ids;
    }

    private Vector<Integer> performCaseIndexQuery(String firstKey, Vector<PredicateProfile> optimizations) {
        //CTS - March 9, 2015 - Introduced a small cache for child index queries here because they
        //are a frequent target of bulk operations like graphing which do multiple requests across the
        //same query.

        PredicateProfile op = optimizations.elementAt(0);

        //TODO: This should likely be generalized for a number of other queries with bulk/nodeset
        //returns
        String indexName = firstKey.substring(Case.INDEX_CASE_INDEX_PRE.length());

        String indexCacheKey = null;

        Vector<Integer> matchingCases = null;

        if (op instanceof IndexedValueLookup) {

            IndexedValueLookup iop = (IndexedValueLookup)op;

            String value = (String)iop.value;

            //TODO: Evaluate whether our indices could contain "|" but I don't imagine how they could.
            indexCacheKey = indexName + "|" + value;

            //Check whether we've got a cache of this index.
            if (mIndexCache.containsKey(indexCacheKey)) {
                //remove the match from the inputs
                optimizations.removeElementAt(0);
                ;
                return mIndexCache.get(indexCacheKey);
            }

            matchingCases = mCaseIndexTable.getCasesMatchingIndex(indexName, value);
        }
        if (op instanceof IndexedSetMemberLookup) {
            IndexedSetMemberLookup sop = (IndexedSetMemberLookup)op;
            matchingCases = mCaseIndexTable.getCasesMatchingValueSet(indexName, sop.valueSet);
        }

        //Clear the most recent index and wipe it, because there is no way it is going to be useful
        //after this
        mMostRecentBatchFetch = new String[2][];

        //remove the match from the inputs
        optimizations.removeElementAt(0);

        if (indexCacheKey != null) {
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
        }
        return matchingCases;
    }

    @Override
    public String getCacheIndex(TreeReference ref) {
        //NOTE: there's no evaluation here as to whether the ref is suitable
        //we only follow one pattern for now and it's evaluated below. 

        loadElements();

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

    @Override
    public String[][] getCachePrimeGuess() {
        return mMostRecentBatchFetch;
    }


    protected Case unusedGetElementOptimization(int recordId) {
        CaseModelFetchCue cue = getQueryPlanner().getQueryCue(CaseModelFetchCue.class);
        if(cue != null && cue.isCueing(recordId)) {
            if(!cue.isPrimed(recordId)) {
                EvaluationTrace loadTrace = new EvaluationTrace("Bulk Case Load");
                SqlStorage<ACase> sqlStorage = ((SqlStorage<ACase>)storage);
                sqlStorage.bulkRead(new ArrayList<>(cue.getCuedCases()), cue.getCaseMap());
                cue.setPrimed();
                loadTrace.setOutcome(String.valueOf(cue.getCuedCases().size()));
                getQueryPlanner().reportTrace(loadTrace);
            }

            return cue.getCase(recordId);
        }

        return super.getElement(recordId);
    }
}
