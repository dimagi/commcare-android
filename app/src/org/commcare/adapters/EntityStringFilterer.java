package org.commcare.adapters;


import com.google.firebase.perf.metrics.Trace;

import androidx.appcompat.app.AppCompatActivity;

import org.commcare.CommCareApplication;
import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.commcare.google.services.analytics.CCPerfMonitoring;
import org.commcare.models.database.IDatabase;
import org.commcare.modern.util.Pair;
import org.commcare.util.EntitySortUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StringUtils;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * Filter entity list via all string-representable entity fields
 */
public class EntityStringFilterer extends EntityFiltererBase {
    private final boolean isFilterEmpty;
    private final String[] searchTerms;
    private final ArrayList<Pair<Integer, Integer>> matchScores = new ArrayList<>();
    private final boolean isFuzzySearchEnabled;

    public EntityStringFilterer(EntityListAdapter adapter,
                                String[] searchTerms,
                                boolean isFuzzySearchEnabled,
                                NodeEntityFactory nodeFactory,
                                List<Entity<TreeReference>> fullEntityList,
                                AppCompatActivity context) {
        super(context, nodeFactory, adapter, fullEntityList);

        this.isFuzzySearchEnabled = isFuzzySearchEnabled;
        this.isFilterEmpty = searchTerms == null || searchTerms.length == 0;
        this.searchTerms = searchTerms;

        if (isFilterEmpty) {
            matchList.addAll(fullEntityList);
        }
    }

    @Override
    protected void filter() {
        long startTime = System.currentTimeMillis();
        Trace trace = CCPerfMonitoring.INSTANCE.startTracing(CCPerfMonitoring.TRACE_CASE_SEARCH_TIME);

        if (!isFilterEmpty) {
            buildMatchList();
        }

        if (isCancelled()) {
            return;
        }

        // If cancelled, the tracing is not to be stopped and Firebase is supposed to discard it
        if (trace != null) {
            Map<String, String> attrs = new HashMap<>();
            attrs.put(CCPerfMonitoring.ATTR_RESULTS_COUNT,
                    String.valueOf((matchList == null ? 0 : matchList.size())));
            attrs.put(CCPerfMonitoring.ATTR_SEARCH_QUERY_LENGTH,
                    String.valueOf((searchTerms == null ? 0 : StringUtils.getSumOfLengths(searchTerms))));
            CCPerfMonitoring.INSTANCE.stopTracing(trace, attrs);
        }

        long time = System.currentTimeMillis() - startTime;
        if (time > 1000) {
            Logger.log("cache", "Presumably finished caching new entities, time taken: " + time + "ms");
        }
    }

    private Entity<TreeReference> getEntityAtIndex(IDatabase db, int index) {
        if (index % 500 == 0) {
            db.yieldIfContendedSafely();
        }
        Entity<TreeReference> e = fullEntityList.get(index);
        if (isCancelled()) {
            return null;
        }
        return e;
    }

    private void buildMatchList() {
        Locale currentLocale = Locale.getDefault();
        //It's a bit sketchy here, because this DB lock will prevent
        //anything else from processing
        IDatabase db;
        try {
            db = CommCareApplication.instance().getUserDbHandle();
        } catch (SessionUnavailableException e) {
            this.cancelSearch();
            return;
        }
        db.beginTransaction();
        try {
            EntitySortUtil.sortEntities(fullEntityList,
                    searchTerms,
                    currentLocale,
                    isFuzzySearchEnabled,
                    matchScores,
                    matchList,
                    index -> getEntityAtIndex(db, index));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
