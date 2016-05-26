package org.commcare.adapters;

import android.app.Activity;

import org.commcare.modern.models.Entity;
import org.commcare.modern.models.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Skeleton for filtering the entity select list.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public abstract class EntityFiltererBase {
    private final NodeEntityFactory nodeFactory;
    private final EntityListAdapter adapter;
    protected final List<Entity<TreeReference>> matchList;
    protected final List<Entity<TreeReference>> fullEntityList;
    private Thread thread;
    private boolean cancelled = false;
    private final Activity context;

    public EntityFiltererBase(Activity context,
                              NodeEntityFactory nodeFactory,
                              EntityListAdapter adapter,
                              List<Entity<TreeReference>> fullEntityList) {
        this.context = context;
        this.nodeFactory = nodeFactory;
        this.adapter = adapter;
        this.fullEntityList = fullEntityList;
        this.matchList = new ArrayList<>();
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
                filter();

                finishSearch();
            }
        });
        thread.start();
    }

    private void finishSearch() {
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.setCurrent(matchList);
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
     * Uses the provided filter logic to build the list of matching
     * entities from the full list of entities
     */
    protected abstract void filter();
}
