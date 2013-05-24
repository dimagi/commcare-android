/**
 * 
 */
package org.commcare.android.fragments;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.tasks.templates.CommCareTask;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
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
	  /**
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
	    	if(this.currentTask != null && this.currentTask.getStatus() != AsyncTask.Status.FINISHED) {
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
	 
	  /**
	   * This method will only be called once when the retained
	   * Fragment is first created.
	   */
	  @Override
	  public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	 
	    // Retain this fragment across configuration changes.
	    setRetainInstance(true);
	  }
	 
	  /**
	   * Set the callback to null so we don't accidentally leak the 
	   * Activity instance.
	   */
	  @Override
	  public void onDetach() {
	    super.onDetach();
	    if(this.lastActivity != null) {
	    	lastActivity = boundActivity;
	    }
		if(currentTask != null) {
			Log.i("CommCareUI", "Detaching activity from current task: " + this.currentTask);
			currentTask.disconnect();
		}
	  }

	public void cancelTask() {
		if(currentTask != null) {
			currentTask.cancel(false);
		}
	}
}
