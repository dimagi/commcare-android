package org.commcare.engine.cases;

import org.commcare.cases.model.Case;
import org.commcare.cases.query.QueryCache;

import java.util.HashMap;
import java.util.LinkedHashSet;

/**
 * Created by ctsims on 1/25/2017.
 */

public class CaseGroupResultCache implements QueryCache {

    static final int MAX_PREFETCH_CASE_BLOCK = 7500;

    private HashMap<String,LinkedHashSet<Integer>> bulkFetchBodies = new HashMap<>();

    private HashMap<Integer, Case> cachedCases = new HashMap<>();


    void reportBulkCaseBody(String key, LinkedHashSet<Integer> ids) {
        if(bulkFetchBodies.containsKey(key)) {
            return;
        }
        bulkFetchBodies.put(key, ids);
    }

    boolean hasMatchingCaseSet(int recordId) {
        if(isLoaded(recordId)) {
            return true;
        }
        if(getTranche(recordId) != null) {
            return true;
        }
        return false;
    }

    LinkedHashSet<Integer> getTranche(int recordId) {
        for(LinkedHashSet<Integer> tranche: bulkFetchBodies.values()) {
            if(tranche.contains(recordId)){
                return tranche;
            }
        }
        return null;
    }

    boolean isLoaded(int recordId) {
        return cachedCases.containsKey(recordId);
    }

    HashMap<Integer, Case> getLoadedCaseMap() {
        return cachedCases;
    }

    Case getLoadedCase(int recordId) {
        return cachedCases.get(recordId);
    }
}
