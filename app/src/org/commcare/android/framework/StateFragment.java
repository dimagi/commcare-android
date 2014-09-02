/**
 * 
 */
package org.commcare.android.framework;

import org.commcare.android.tasks.templates.CommCareTask;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.Fragment;
import android.util.Log;
/**
 * @author ctsims
 *
 */
public class StateFragment extends Fragment {
	
	CommCareActivity boundActivity;
	CommCareActivity lastActivity;
	
	CommCareTask currentTask;
	/*
	 * (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onAttach(android.app.Activity)
	 * 
	 * Hold a reference to the parent Activity so we can report the
	 * task's current progress and results. The Android framework 
	 * will pass us a reference to the newly created Activity after 
	 * each configuration change.
	 */
	  @Override
	  public void onAttach(Activity activity) {
	    super.onAttach(activity);
	    if(activity instanceof CommCareActivity) {
	    	this.boundActivity = (CommCareActivity)activity;
	    	this.boundActivity.stateHolder = this;
	    	if(this.currentTask != null && this.currentTask.getStatus() == AsyncTask.Status.RUNNING) {
	    		this.currentTask.connect(boundActivity);
	    	}
	    }
	  }
	  
	  public CommCareActivity getPreviousState() { 
		  return lastActivity;
	  }
	  
	  public void connectTask(CommCareTask task) {
		  this.currentTask = task;
	  }
	 
	  /*
	   * (non-Javadoc)
	   * @see android.support.v4.app.Fragment#onCreate(android.os.Bundle)
	   * 
	   * This method will only be called once when the retained
	   * Fragment is first created.
	   */
	  @Override
	  public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	 
	    // Retain this fragment across configuration changes.
	    setRetainInstance(true);
	  }
	 
	  /*
	   * (non-Javadoc)
	   * @see android.support.v4.app.Fragment#onDetach()
	   * 
	   * Set the callback to null so we don't accidentally leak the 
	   * Activity instance.
	   */
	  @Override
	  public void onDetach() {
	    super.onDetach();
	    if(this.boundActivity != null) {
	    	lastActivity = boundActivity;
	    }
		if(currentTask != null) {
			Log.i("CommCareUI", "Detaching activity from current task: " + this.currentTask);
			currentTask.disconnect();
			unlock();
		}
	  }

	public void cancelTask() {
		if(currentTask != null) {
			currentTask.cancel(false);
		}
	}

    private WakeLock wakelock;

	public synchronized void wakelock(int lockLevel) {
    	if(wakelock != null) {
    		if(wakelock.isHeld()) {
    			wakelock.release();
    		}
    	}
    	PowerManager pm = (PowerManager) boundActivity.getSystemService(Context.POWER_SERVICE);
    	wakelock = pm.newWakeLock(lockLevel, "CommCareLock");
    	wakelock.acquire();
	}

	public synchronized void unlock() {
    	if(wakelock != null) {
    		wakelock.release();
    		wakelock = null;
    	}

	}
}
