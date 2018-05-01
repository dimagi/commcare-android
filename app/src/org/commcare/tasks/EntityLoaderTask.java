package org.commcare.tasks;

import android.util.Pair;

import org.commcare.activities.EntitySelectActivity;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.models.AsyncNodeEntityFactory;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.suite.model.Detail;
import org.commcare.tasks.templates.ManagedAsyncTask;
import org.commcare.util.LogTypes;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ctsims
 */
public class EntityLoaderTask
        extends ManagedAsyncTask<TreeReference, Integer, Pair<List<Entity<TreeReference>>, List<TreeReference>>> {

    private final static Object lock = new Object();
    private static EntityLoaderTask pendingTask = null;

    private final NodeEntityFactory factory;
    private EntityLoaderListener listener;
    private Exception mException = null;
    private int focusTargetIndex;

    public EntityLoaderTask(Detail detail, EvaluationContext evalCtx) {
        evalCtx.addFunctionHandler(EntitySelectActivity.getHereFunctionHandler());
        if (detail.useAsyncStrategy()) {
            this.factory = new AsyncNodeEntityFactory(detail, evalCtx);
        } else {
            this.factory = new NodeEntityFactory(detail, evalCtx);
            if (DeveloperPreferences.collectAndDisplayEntityTraces()) {
                this.factory.activateDebugTraceOutput();
            }
        }
    }

    @Override
    protected Pair<List<Entity<TreeReference>>, List<TreeReference>> doInBackground(TreeReference... nodeset) {
        try {
            List<TreeReference> references = factory.expandReferenceList(nodeset[0]);

            List<Entity<TreeReference>> full = new ArrayList<>();
            focusTargetIndex = -1;
            int indexInFullList = 0;
            for (TreeReference ref : references) {
                if (this.isCancelled()) {
                    return null;
                }

                Entity<TreeReference> e = factory.getEntity(ref);
                if (e != null) {
                    full.add(e);
                    if (e.shouldReceiveFocus()) {
                        focusTargetIndex = indexInFullList;
                    }
                    indexInFullList++;
                }
            }

            factory.prepareEntities(full);
            factory.printAndClearTraces("build");
            return new Pair<>(full, references);
        } catch (XPathException xe) {
            XPathErrorLogger.INSTANCE.logErrorToCurrentApp(xe);
            xe.printStackTrace();
            Logger.exception("Error during EntityLoaderTask: " + ForceCloseLogger.getStackTrace(xe), xe);
            mException = xe;
            return null;
        }
    }

    @Override
    protected void onPostExecute(Pair<List<Entity<TreeReference>>, List<TreeReference>> result) {
        super.onPostExecute(result);

        long waitingTime = System.currentTimeMillis();
        //Ok. So. time to try to deliver the result
        while (true) {
            synchronized (lock) {
                if (listener != null) {
                    pendingTask = null;

                    if (mException != null) {
                        listener.deliverLoadError(mException);
                        return;
                    }

                    listener.deliverLoadResult(result.first, result.second, factory, focusTargetIndex);

                    return;
                }
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // If this is pending for more than about a second, drop it, we
            // never know if it's going to get reattached
            if (System.currentTimeMillis() - waitingTime > 1000) {
                pendingTask = null;
                return;
            }
        }
    }

    public void detachActivity() {
        synchronized (lock) {
            pendingTask = this;
        }
    }

    public static boolean attachToActivity(EntityLoaderListener listener) {
        synchronized (lock) {
            if (pendingTask == null) {
                return false;
            }
            EntityLoaderTask task = pendingTask;
            task.attachListener(listener);
            pendingTask = null;
            return true;
        }
    }

    public void attachListener(EntityLoaderListener listener) {
        this.listener = listener;
        listener.attachLoader(this);
    }
}
