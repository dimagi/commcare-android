/**
 * 
 */
package org.commcare.android.tasks;

import java.util.ArrayList;
import java.util.List;

import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathMissingInstanceException;

import android.os.AsyncTask;
import android.util.Pair;


/**
 * @author ctsims
 *
 */
public class EntityLoaderTask extends AsyncTask<TreeReference, Integer, Pair<List<Entity<TreeReference>>, List<TreeReference>>> {
	
	private static EntityLoaderTask pending[] = {null};
	
	NodeEntityFactory factory;
	EvaluationContext ec;
	EntityLoaderListener listener;
	Exception mException = null;
	
	private long waitingTime; 

	public EntityLoaderTask(Detail d, EvaluationContext ec) {
		this.factory = new NodeEntityFactory(d, ec);
		this.ec = ec;
	}
	
	public void attachListener(EntityLoaderListener listener){ 
		this.listener = listener;
		listener.attach(this);
	}

	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
	 */
	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
	}
	

	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
	 */
	@Override
	protected void onPostExecute(Pair<List<Entity<TreeReference>>, List<TreeReference>> result) {
		super.onPostExecute(result);
		
		if(mException != null){
			listener.deliverError(mException);
			return;
		}
		
		waitingTime = System.currentTimeMillis();
		//Ok. So. time to try to deliver the result
		while(true) {
			//grab the lock
			synchronized(pending) {
				//If our listener is still live, we can deliver our result
				if(listener != null) {
					//pass those params
					listener.deliverResult(result.first, result.second);
					this.pending[0] = null;
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
		List<TreeReference> references = ec.expandReference(nodeset[0]);
		
		List<Entity<TreeReference>> full = new ArrayList<Entity<TreeReference>>(); 
		for(TreeReference ref : references) {
			
			if(this.isCancelled()) { return null; }
			
			Entity<TreeReference> e = factory.getEntity(ref);
			if(e != null) {
				full.add(e);
			}
		}
		
		return new Pair<List<Entity<TreeReference>>, List<TreeReference>>(full, references);
		
		} catch (XPathMissingInstanceException xme){
			mException = xme;
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
