package org.commcare.adapters;

import android.support.v7.app.AppCompatActivity;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.commcare.cases.util.StringUtils;
import org.commcare.modern.util.Pair;
import org.commcare.util.EntityProvider;
import org.commcare.util.EntitySortUtil;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


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

        if (!isFilterEmpty) {
            buildMatchList();
        }

        if (isCancelled()) {
            return;
        }

        long time = System.currentTimeMillis() - startTime;
        if (time > 1000) {
            Logger.log("cache", "Presumably finished caching new entities, time taken: " + time + "ms");
        }
    }

    private Entity<TreeReference> getEntityAtIndex(SQLiteDatabase db, int index) {
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
        SQLiteDatabase db;
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
                    new EntityProvider() {
                        @Override
                        public Entity<TreeReference> getEntity(int index) {
                            return getEntityAtIndex(db, index);
                        }
                    });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
