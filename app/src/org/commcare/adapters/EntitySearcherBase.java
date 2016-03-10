package org.commcare.adapters;

import org.commcare.models.NodeEntityFactory;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public abstract class EntitySearcherBase {
    private final NodeEntityFactory nodeFactory;
    private Thread thread;
    private boolean cancelled = false;

    public EntitySearcherBase(NodeEntityFactory nodeFactory) {
        this.nodeFactory = nodeFactory;
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
        cancelled = true;
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
}
