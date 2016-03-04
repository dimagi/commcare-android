package org.commcare.adapters;

import android.app.Activity;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StringUtils;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class EntitySearcher {
    private final boolean isFilterEmpty;
    private final String[] searchTerms;
    private final List<Entity<TreeReference>> matchList;
    private final ArrayList<Pair<Integer, Integer>> matchScores = new ArrayList<>();
    private boolean cancelled = false;
    private final boolean isAsyncMode;
    private final boolean isFuzzySearchEnabled;
    private final NodeEntityFactory nodeFactory;
    private final List<Entity<TreeReference>> full;
    private final Activity context;
    private final EntityListAdapter adapter;
    private Thread thread;

    public EntitySearcher(EntityListAdapter adapter,
                          String[] searchTerms,
                          boolean isAsyncMode, boolean isFuzzySearchEnabled,
                          NodeEntityFactory nodeFactory,
                          List<Entity<TreeReference>> full,
                          Activity context) {
        this.adapter = adapter;
        this.full = full;
        this.context = context;
        this.isAsyncMode = isAsyncMode;
        this.isFuzzySearchEnabled = isFuzzySearchEnabled;
        this.nodeFactory = nodeFactory;
        this.isFilterEmpty = searchTerms == null || searchTerms.length == 0;
        this.searchTerms = searchTerms;
        if (isFilterEmpty) {
            matchList = new ArrayList<>(full);
        } else {
            matchList = new ArrayList<>();
        }
    }

    public void start() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                //Make sure that we have loaded the necessary cached data
                //before we attempt to search over it
                while (!nodeFactory.isEntitySetReady()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                search();
            }
        });
        thread.start();
    }

    public void finish() {
        this.cancelled = true;
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void search() {
        long startTime = System.currentTimeMillis();

        if (!isFilterEmpty) {
            buildMatchList();
        }

        if (cancelled) {
            return;
        }

        long time = System.currentTimeMillis() - startTime;
        if (time > 1000) {
            Logger.log("cache", "Presumably finished caching new entities, time taken: " + time + "ms");
        }

        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.setCurrent(matchList);
                adapter.setCurrentSearchTerms(searchTerms);
                adapter.update();
            }
        });
    }

    private void buildMatchList() {
        Locale currentLocale = Locale.getDefault();
        //It's a bit sketchy here, because this DB lock will prevent
        //anything else from processing
        SQLiteDatabase db;
        try {
            db = CommCareApplication._().getUserDbHandle();
        } catch (SessionUnavailableException e) {
            this.finish();
            return;
        }
        db.beginTransaction();
        for (int index = 0; index < full.size(); ++index) {
            //Every once and a while we should make sure we're not blocking anything with the database
            if (index % 500 == 0) {
                db.yieldIfContendedSafely();
            }
            Entity<TreeReference> e = full.get(index);
            if (cancelled) {
                break;
            }

            boolean add = false;
            int score = 0;
            filter:
            for (String filter : searchTerms) {
                add = false;
                for (int i = 0; i < e.getNumFields(); ++i) {
                    String field = e.getNormalizedField(i);
                    if (!"".equals(field) && field.toLowerCase(currentLocale).contains(filter)) {
                        add = true;
                        continue filter;
                    } else {
                        // We possibly now want to test for edit distance for
                        // fuzzy matching
                        if (isFuzzySearchEnabled) {
                            for (String fieldChunk : e.getSortFieldPieces(i)) {
                                Pair<Boolean, Integer> match = StringUtils.fuzzyMatch(filter, fieldChunk);
                                if (match.first) {
                                    add = true;
                                    score += match.second;
                                    continue filter;
                                }
                            }
                        }
                    }
                }
                if (!add) {
                    break;
                }
            }
            if (add) {
                //matchList.add(e);
                matchScores.add(Pair.create(index, score));
            }
        }
        if (isAsyncMode) {
            Collections.sort(matchScores, new Comparator<Pair<Integer, Integer>>() {
                @Override
                public int compare(Pair<Integer, Integer> lhs, Pair<Integer, Integer> rhs) {
                    return lhs.second - rhs.second;
                }
            });
        }

        for (Pair<Integer, Integer> match : matchScores) {
            matchList.add(full.get(match.first));
        }

        db.setTransactionSuccessful();
        db.endTransaction();
    }
}
