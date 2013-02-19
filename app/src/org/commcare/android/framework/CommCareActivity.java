/**
 * 
 */
package org.commcare.android.framework;

import java.lang.reflect.Field;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.tasks.templates.CommCareTaskConnector;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * Base class for CommCareActivities to simplify 
 * common localization and workflow tasks
 * 
 * @author ctsims
 *
 */
public abstract class CommCareActivity<R> extends Activity implements CommCareTaskConnector<R> {
	
	protected final static int DIALOG_PROGRESS = 32;
	protected final static String DIALOG_TEXT = "cca_dialog_text";
	
	CommCareTask currentTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		//TODO: We can really handle much of this framework without needing to 
		//be a superclass.
		super.onCreate(savedInstanceState);
		if(this.getClass().isAnnotationPresent(ManagedUi.class)) {
			this.setContentView(this.getClass().getAnnotation(ManagedUi.class).value());
			loadFields();
		}
	}
	
	private void loadFields() {
		CommCareActivity oldActivity = null;
		Object o = this.getLastNonConfigurationInstance();
		if(o instanceof CommCareActivity) {
			oldActivity = (CommCareActivity)o;
		}
		Class c = this.getClass();
		for(Field f : c.getDeclaredFields()) {
			if(f.isAnnotationPresent(UiElement.class)) {
				UiElement element = f.getAnnotation(UiElement.class);
				try{
					f.setAccessible(true);
					
					try {
						View v = this.findViewById(element.value());
						f.set(this, v);
						
						if(oldActivity != null) {
							View oldView = (View)f.get(oldActivity);
							if(oldView != null) {
								if(v instanceof TextView) {
									((TextView)v).setText(((TextView)oldView).getText());
								}
								v.setVisibility(oldView.getVisibility());
								continue;
							}
						}
						
						if(element.locale() != "") {
							if(v instanceof TextView) {
								((TextView)v).setText(Localization.get(element.locale()));
							} else {
								throw new RuntimeException("Can't set the text for a " + v.getClass().getName() + " View!");
							}
						}
					} catch (IllegalArgumentException e) {
						throw new RuntimeException("Bad Object type for field " + f.getName());
					} catch (IllegalAccessException e) {
						throw new RuntimeException("Couldn't access the activity field for some reason");
					}
				} finally {
					f.setAccessible(false);
				}
			}
		}
	}
	

	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		Object o = this.getLastNonConfigurationInstance();
		if(o != null && o instanceof CommCareActivity) {
			//Time to reconnect with our roots
			CommCareActivity a = (CommCareActivity)o;
			this.connectTask(a.currentTask);
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onRetainNonConfigurationInstance()
	 */
	@Override
	public final Object onRetainNonConfigurationInstance() {
		return this;
	}
	
	
	protected void updateProgress(int taskId, String updateText) {
		Bundle b = new Bundle();
		b.putString(DIALOG_TEXT, updateText);
		this.showDialog(taskId, b);
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#connectTask(org.commcare.android.tasks.templates.CommCareTask)
	 */
	@Override
	public <A, B, C> void connectTask(CommCareTask<A, B, C, R> task) {
		currentTask = task;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#getReceiver()
	 */
	@Override
	public R getReceiver() {
		return (R)this;
	}
	
	/**
	 * Override these to control the UI for your task
	 */

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#startBlockingForTask()
	 */
	@Override
	public void startBlockingForTask(int id) {
		this.showDialog(id);
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#stopBlockingForTask()
	 */
	@Override
	public void stopBlockingForTask(int id) {
		this.dismissDialog(id);
	}
	
    
    /* (non-Javadoc)
	 * @see android.app.Activity#onPrepareDialog(int, android.app.Dialog, android.os.Bundle)
	 */
	@Override
	protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
		super.onPrepareDialog(id, dialog, args);
		if(dialog instanceof ProgressDialog) {
			if(args != null && args.containsKey(CommCareActivity.DIALOG_TEXT)) {
				((ProgressDialog)dialog).setMessage(args.getString(CommCareActivity.DIALOG_TEXT));
			}
		}
	}
	

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#taskCancelled(int)
	 */
	@Override
	public void taskCancelled(int id) {
		// TODO Auto-generated method stub
		
	}
}
