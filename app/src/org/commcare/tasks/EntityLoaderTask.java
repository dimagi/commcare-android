package org.commcare.tasks;

import android.util.Pair;

import com.google.firebase.perf.metrics.Trace;

import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.EntityLoadingProgressListener;
import org.commcare.google.services.analytics.CCAnalyticsParam;
import org.commcare.google.services.analytics.CCPerfMonitoring;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.EntityDatum;
import org.commcare.tasks.templates.ManagedAsyncTask;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathException;

import java.util.List;

import javax.annotation.Nullable;

/**
 * @author ctsims
 */
public class EntityLoaderTask
        extends
        ManagedAsyncTask<TreeReference, Integer, Pair<List<Entity<TreeReference>>, List<TreeReference>>> implements
        EntityLoadingProgressListener {

    private final static Object lock = new Object();
    private static EntityLoaderTask pendingTask = null;

    private EntityLoaderListener listener;
    private final EntityLoaderHelper entityLoaderHelper;
    private Exception mException = null;
    private boolean provideDetailProgressUpdates;

    /**
     * Creates a new instance
     *
     * @param detail      detail we want to load
     * @param entityDatum entity datum corresponding to the entity list, null for entity detail screens
     * @param evalCtx     evaluation context
     */
    public EntityLoaderTask(Detail detail, @Nullable EntityDatum entityDatum, EvaluationContext evalCtx) {
        entityLoaderHelper = new EntityLoaderHelper(detail, entityDatum, evalCtx, false, null);
        // we only want to provide progress updates for the new caching config
        provideDetailProgressUpdates = detail.shouldOptimize();
    }

    @Override
    protected Pair<List<Entity<TreeReference>>, List<TreeReference>> doInBackground(TreeReference... nodeset) {
        try {
            // Capture sync_case_list_loading trace for performance monitoring
            Trace trace = CCPerfMonitoring.INSTANCE.createTrace(CCPerfMonitoring.SYNC_ENTITY_LIST_LOADING);
            if (trace != null && !entityLoaderHelper.isAsyncNodeEntityFactory()) {
                trace.putAttribute(CCAnalyticsParam.CCHQ_DOMAIN, ReportingUtils.getDomain());
                trace.putAttribute(CCAnalyticsParam.USERNAME, ReportingUtils.getUser());
                trace.start();
            }
            Pair<List<Entity<TreeReference>>, List<TreeReference>> entities = entityLoaderHelper.loadEntities(nodeset[0], this);

            if (trace != null && !entityLoaderHelper.isAsyncNodeEntityFactory()) {
                trace.putAttribute(CCPerfMonitoring.NUM_CASES_LOADED,
                        String.valueOf((entities == null || entities.first == null ? 0 : entities.first.size())));
                trace.stop();
            }
            return entities;
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
    public void publishEntityLoadingProgress(EntityLoadingProgressPhase phase, int progress, int total) {
        publishProgress(phase.getValue(), progress, total);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (listener != null && provideDetailProgressUpdates) {
            listener.deliverProgress(values);
        }
    }
}
