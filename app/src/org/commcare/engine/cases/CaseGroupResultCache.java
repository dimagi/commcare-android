package org.commcare.engine.cases;

import android.util.SparseArray;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntSet;

import org.commcare.cases.model.Case;
import org.commcare.cases.query.QueryCache;
import org.commcare.cases.query.QueryCacheEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Vector;

/**
 * Created by ctsims on 1/25/2017.
 */

public class CaseGroupResultCache implements QueryCacheEntry {

    public static final int MAX_PREFETCH_CASE_BLOCK = 7500;

    HashMap<String,LinkedHashSet<Integer>> bulkFetchBodies = new HashMap<>();

    HashMap<Integer, Case> cachedCases = new HashMap<>();


    public void reportBulkCaseBody(String key, LinkedHashSet<Integer> ids) {
        if(bulkFetchBodies.containsKey(key)) {
            return;
        }
        bulkFetchBodies.put(key, ids);
    }

    public boolean hasMatchingCaseSet(int recordId) {
        if(isLoaded(recordId)) {
            return true;
        }
        if(getTranche(recordId) != null) {
            return true;
        }
        return false;
    }

    public LinkedHashSet<Integer> getTranche(int recordId) {
        for(LinkedHashSet<Integer> tranche: bulkFetchBodies.values()) {
            if(tranche.contains(recordId)){
                return tranche;
            }
        }
        return null;
    }

    public boolean isLoaded(int recordId) {
        return cachedCases.containsKey(recordId);
    }

    public HashMap<Integer, Case> getLoadedCaseMap() {
        return cachedCases;
    }

    public Case getLoadedCase(int recordId) {
        return cachedCases.get(recordId);
    }
}
