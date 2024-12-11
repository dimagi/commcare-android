package org.commcare.tasks;

import android.util.Pair;

import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.cases.entity.Entity;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.suite.model.Detail;
import org.commcare.tasks.templates.ManagedAsyncTask;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathException;

import java.util.List;

/**
 * @author ctsims
 */
public class EntityLoaderTask
        extends ManagedAsyncTask<TreeReference, Integer, Pair<List<Entity<TreeReference>>, List<TreeReference>>> implements
        EntityLoadingProgressListener {

    private final static Object lock = new Object();
    private static EntityLoaderTask pendingTask = null;

    private EntityLoaderListener listener;
    private final EntityLoaderHelper entityLoaderHelper;
    private Exception mException = null;

    public EntityLoaderTask(Detail detail, EvaluationContext evalCtx) {
        entityLoaderHelper = new EntityLoaderHelper(detail, evalCtx);
    }

    @Override
    protected Pair<List<Entity<TreeReference>>, List<TreeReference>> doInBackground(TreeReference... nodeset) {
        try {
            return entityLoaderHelper.loadEntities(nodeset[0], this);
        } catch (XPathException xe) {
            XPathErrorLogger.INSTANCE.logErrorToCurrentApp(xe);
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

                    if (result == null) {
                        return;
                    }

                    listener.deliverLoadResult(result.first, result.second, entityLoaderHelper.getFactory(),
                            entityLoaderHelper.getFocusTargetIndex());
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

    @Override
    protected void onCancelled() {
        super.onCancelled();
        entityLoaderHelper.cancel();
    }

    @Override
    public void publishEntityLoadingProgress(int progress, int total) {
        publishProgress(progress, total);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        listener.deliverProgress(values);
    }
}
