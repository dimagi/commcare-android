package org.commcare.engine.cases;

import org.commcare.cases.model.CaseIndex;
import org.commcare.cases.query.QueryContext;
import org.commcare.cases.query.queryset.CaseModelQuerySetMatcher;
import org.commcare.cases.query.queryset.CaseQuerySetLookup;
import org.commcare.cases.query.queryset.DerivedCaseQueryLookup;
import org.commcare.cases.query.queryset.DualTableSingleMatchModelQuerySet;
import org.commcare.cases.query.queryset.ModelQuerySet;
import org.commcare.cases.query.queryset.QuerySetLookup;
import org.commcare.cases.query.queryset.QuerySetTransform;
import org.commcare.models.database.user.models.CaseIndexTable;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.trace.EvaluationTrace;

import java.util.Set;

/**
 * Created by ctsims on 2/6/2017.
 */

public class CaseIndexQuerySetTransform implements QuerySetTransform {

    private final CaseIndexTable table;

    public CaseIndexQuerySetTransform(CaseIndexTable table) {
        this.table = table;
    }

    @Override
    public QuerySetLookup getTransformedLookup(QuerySetLookup incoming, TreeReference relativeLookup) {
        if(incoming.getQueryModelId().equals(CaseQuerySetLookup.CASE_MODEL_ID)) {
            if(relativeLookup.size() == 2 && "index".equals(relativeLookup.getName(0))) {
                String indexName = relativeLookup.getName(1);
                return new CaseIndexQuerySetLookup(indexName, incoming, table);
            }
        }
        return null;
    }



    public static class CaseIndexQuerySetLookup extends DerivedCaseQueryLookup {
        String indexName;
        CaseIndexTable table;

        public CaseIndexQuerySetLookup(String indexName, QuerySetLookup incoming, CaseIndexTable table) {
            super(incoming);
            this.indexName = indexName;
            this.table = table;
        }

        @Override
        protected ModelQuerySet loadModelQuerySet(QueryContext queryContext) {
            EvaluationTrace trace = new EvaluationTrace("Load Query Set Transform[" +
                    getRootLookup().getCurrentQuerySetId() + "]=>[" +
                    this.getCurrentQuerySetId()+ "]");


            Set<Integer> querySetBody = getRootLookup().getLookupSetBody(queryContext);
            DualTableSingleMatchModelQuerySet ret = table.bulkReadIndexToCaseIdMatch(indexName, querySetBody);
            cacheCaseModelQuerySet(queryContext, ret);

            trace.setOutcome("Loaded: " + ret.getSetBody().size());

            queryContext.reportTrace(trace);
            return ret;
        }

        private void cacheCaseModelQuerySet(QueryContext queryContext, DualTableSingleMatchModelQuerySet ret) {
            if(ret.getSetBody().size() > 50 && ret.getSetBody().size() < CaseGroupResultCache.MAX_PREFETCH_CASE_BLOCK) {
                queryContext.getQueryCache(CaseGroupResultCache.class).reportBulkCaseBody(this.getCurrentQuerySetId(), ret.getSetBody());
            }
        }

        @Override
        public String getQueryModelId() {
            return getRootLookup().getQueryModelId();
        }

        @Override
        public String getCurrentQuerySetId() {
            return getRootLookup().getCurrentQuerySetId() + "|index|"+indexName;
        }
    }
}
