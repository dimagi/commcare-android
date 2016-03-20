package org.commcare.adapters;

import android.app.Activity;

import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.javarosa.core.model.instance.TreeReference;

import java.util.List;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public abstract class EntitySearcherBase {
    private final NodeEntityFactory nodeFactory;
    private final EntityListAdapter adapter;
    private Thread thread;
    private boolean cancelled = false;
    private final Activity context;

    public EntitySearcherBase(Activity context,
                              NodeEntityFactory nodeFactory,
                              EntityListAdapter adapter) {
        this.context = context;
        this.nodeFactory = nodeFactory;
        this.adapter = adapter;
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

    protected abstract void search();

    protected abstract List<Entity<TreeReference>> getMatchList();
}
