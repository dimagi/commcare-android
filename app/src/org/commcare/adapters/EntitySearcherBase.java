package org.commcare.adapters;

import android.app.Activity;

import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.List;

/**
 * Filtering the entity select list using pluggable search logic
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public abstract class EntitySearcherBase {
    private final NodeEntityFactory nodeFactory;
    private final EntityListAdapter adapter;
    protected final List<Entity<TreeReference>> fullEntityList;
    private Thread thread;
    private boolean cancelled = false;
    private final Activity context;

    public EntitySearcherBase(Activity context,
                              NodeEntityFactory nodeFactory,
                              EntityListAdapter adapter,
                              List<Entity<TreeReference>> fullEntityList) {
        this.context = context;
        this.nodeFactory = nodeFactory;
        this.adapter = adapter;
        this.fullEntityList = fullEntityList;
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

                finishSearch();
            }
        });
        thread.start();
    }

    private void finishSearch() {
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.setCurrent(getMatchList());
                adapter.update();
            }
        });
    }

    public void cancelSearch() {
        cancelled = true;
        adapter.clearSearch();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected boolean isCancelled() {
        return cancelled;
    }

    /**
     * Uses the provided search logic to build the list of matching
     * entities from the full list of entities
     */
    protected abstract void search();

    /**
     * @return search results; those entities from the full list that fulfill the search conditions
     */
    protected abstract List<Entity<TreeReference>> getMatchList();
}
