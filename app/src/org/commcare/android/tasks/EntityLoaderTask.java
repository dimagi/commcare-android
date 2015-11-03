package org.commcare.android.tasks;

import android.util.Pair;

import org.commcare.android.analytics.XPathErrorStats;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.AsyncNodeEntityFactory;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.tasks.templates.ManagedAsyncTask;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathException;

import java.util.ArrayList;
import java.util.List;


/**
 * @author ctsims
 *
 */
public class EntityLoaderTask extends ManagedAsyncTask<TreeReference, Integer, Pair<List<Entity<TreeReference>>, List<TreeReference>>> {
    
    private static EntityLoaderTask pending[] = {null};
    
    NodeEntityFactory factory;
    EvaluationContext ec;
    EntityLoaderListener listener;
    Exception mException = null;
    
    private long waitingTime; 

    public EntityLoaderTask(Detail d, EvaluationContext ec) {
        if(d.useAsyncStrategy()) {
            this.factory = new AsyncNodeEntityFactory(d, ec);
        } else {
            this.factory = new NodeEntityFactory(d, ec);
        }
        this.ec = ec;
    }
    
    public void attachListener(EntityLoaderListener listener){ 
        this.listener = listener;
        listener.attach(this);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
    }
    

    @Override
    protected void onPostExecute(Pair<List<Entity<TreeReference>>, List<TreeReference>> result) {
        super.onPostExecute(result);
        
        waitingTime = System.currentTimeMillis();
        //Ok. So. time to try to deliver the result
        while(true) {
            //grab the lock
            synchronized(pending) {
                //If our listener is still live, we can deliver our result
                if(listener != null) {
                    
                    //zero this out to free up reference. this is used as an indicator below to determine if work still needs to be done
                    this.pending[0] = null;
                    
                    // if we have encountered an exception, deliver it and return
                    if(mException != null){
                        listener.deliverError(mException);
                        return;
                    }
                    
                    //pass those params
                    listener.deliverResult(result.first, result.second, factory);
                    
                    return;
                }
                
                //If our listener is _not_ alive 
            }
            //Wait
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            //If this is pending for more than about a second, drop it, we never know if it's going to get reattached
            if(System.currentTimeMillis() - waitingTime > 1000) {
                pending[0] = null;
                return;
            }
        }
        
    }

    @Override
    protected Pair<List<Entity<TreeReference>>, List<TreeReference>> doInBackground(TreeReference... nodeset) {
        try{
            List<TreeReference> references = factory.expandReferenceList(nodeset[0]);

            List<Entity<TreeReference>> full = new ArrayList<Entity<TreeReference>>();
            for(TreeReference ref : references) {

                if(this.isCancelled()) { return null; }

                Entity<TreeReference> e = factory.getEntity(ref);
                if(e != null) {
                    full.add(e);
                }
            }

            factory.prepareEntities();
            return new Pair<List<Entity<TreeReference>>, List<TreeReference>>(full, references);
        } catch (XPathException xe){
            XPathErrorStats.logErrorToCurrentApp(xe);

            XPathException me = new XPathException("Encountered an xpath error while trying to load and filter the list.");
            me.setSource(xe.getSource());
            xe.printStackTrace();
            Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, ExceptionReportTask.getStackTrace(me));
            mException = me;
            return null;
        }
    }

    /**
     * detach the activity and 
     */
    public void detachActivity() {
        synchronized(pending) {
            pending[0] = this;
        }
    }
    
    public static boolean attachToActivity(EntityLoaderListener listener) {
        synchronized(pending) {
            if(pending[0] == null) {
                return false;
            }
            EntityLoaderTask task = pending[0];
            task.attachListener(listener);
            pending[0] = null;
            return true;
        }
    }
}
