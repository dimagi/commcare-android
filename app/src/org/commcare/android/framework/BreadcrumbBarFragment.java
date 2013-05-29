/**
 * 
 */
package org.commcare.android.framework;

import java.util.Vector;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCareSession;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BreadcrumbBarFragment extends Fragment {
	
	private boolean isTopNavEnabled = false;
	 
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
	   * Hold a reference to the parent Activity so we can report the
	   * task's current progress and results. The Android framework 
	   * will pass us a reference to the newly created Activity after 
	   * each configuration change.
	   */
	  @Override
	  public void onAttach(Activity activity) {
		    super.onAttach(activity);
		    
		    String activityTitle = null;
		    
		    if(activity instanceof CommCareActivity) {
		    	activityTitle = ((CommCareActivity)activity).getActivityTitle();
		    	isTopNavEnabled = ((CommCareActivity)activity).isTopNavEnabled();
		    }
		    
		    ActionBar actionBar = activity.getActionBar();
		    actionBar.setCustomView(getTitleView(activity, activityTitle), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		    activity.setTitle("");
		    actionBar.setDisplayShowHomeEnabled(false);

	  }
	  
		
		public View getTitleView(final Activity activity, String local) {
			RelativeLayout layout = new RelativeLayout(activity);
			layout.setGravity(Gravity.CENTER_VERTICAL);
			
			// we have to do this walk backwards, actually
			
			String topLevel = CommCareActivity.getTopLevelTitleName(activity);
			LayoutInflater li = activity.getLayoutInflater();
			
			int currentId = -1;
			
			//We don't actually want this one to look the same
			int newId = org.commcare.dalvik.R.id.component_title_breadcrumb_text + layout.getChildCount() + 1;
			if(local != null) {
				View titleBreadcrumb = li.inflate(org.commcare.dalvik.R.layout.component_title_uncrumb, layout, true);
				
				TextView text = (TextView)titleBreadcrumb.findViewById(org.commcare.dalvik.R.id.component_title_breadcrumb_text);
				
				text.setText(local);
				//Is there a "random ID" or something we can use for this?
				text.setId(newId);
				//RelativeLayout.LayoutParams layout = (RelativeLayout.LayoutParams)peerView.getLayoutParams();
				RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)text.getLayoutParams();
				params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				params.addRule(RelativeLayout.CENTER_VERTICAL);
				text.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
				
				text.setLayoutParams(params);
			}
			//int newId = addElementToTitle(li, layout, local, org.commcare.dalvik.R.layout.component_title_uncrumb, -1, null);
			
			if(newId != -1) { currentId = newId;}
			
			String[] stepTitles = new String[0];
			try {
				stepTitles = CommCareApplication._().getCurrentSession().getHeaderTitles();
				
				//See if we can insert any case hacks
				Vector<String[]> v = CommCareApplication._().getCurrentSession().getSteps();
				for(int i = v.size() -1 ; i >= 0; i--){
					String[] step = v.elementAt(i);
					final int currentStep = i;
					final int currentStepSize = v.size();
				
					OnClickListener stepBackListener = new OnClickListener() {

						@Override
						public void onClick(View arg0) {
							
							int stepsToTake = currentStepSize - currentStep - 1;
							
							try{
								for(int i = 0 ; i < stepsToTake ; i++) {
									CommCareApplication._().getCurrentSession().stepBack();
									int currentStepSize = CommCareApplication._().getCurrentSession().getSteps().size();
									
									//Take at _most_ currentSteps back, or stop when we've reached
									//current step minus 1
									if(currentStepSize == 0  || currentStepSize < currentStep) {
										break;
									}
								}
								
								activity.finish();
							} catch(SessionUnavailableException sue) {
								
							}
						}
						
					};
				
					

					try {
					if(CommCareSession.STATE_DATUM_VAL.equals(step[0])) {
						//Haaack
						if("case_id".equals(step[1])) {
							ACase foundCase = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class).getRecordForValue(ACase.INDEX_CASE_ID, step[2]);
							stepTitles[i] = foundCase.getName();
							newId = addElementToTitle(li, layout, stepTitles[i], org.commcare.dalvik.R.layout.component_title_breadcrumb_case, currentId, stepBackListener);
							if(newId != -1) { currentId = newId;}
							continue;
						}
					}
					} catch(Exception e) {
						//TODO: Your error handling is bad and you should feel bad
					}
					newId = addElementToTitle(li, layout, stepTitles[i], org.commcare.dalvik.R.layout.component_title_breadcrumb, currentId, stepBackListener);
					if(newId != -1) { currentId = newId;}
				}
				
			} catch(SessionUnavailableException sue) {
				
			}
			
			addElementToTitle(li, layout, topLevel, org.commcare.dalvik.R.layout.component_title_breadcrumb, currentId, new OnClickListener() {

				@Override
				public void onClick(View arg0) {
					try{
						CommCareApplication._().getCurrentSession().clearState();
					} catch(SessionUnavailableException sue) {
						
					}
					
					activity.finish();
				}
				
			});
			
			//Add the app icon
			TextView iconBearer = ((TextView)layout.getChildAt(layout.getChildCount() - 1));
			
			iconBearer.setCompoundDrawablesWithIntrinsicBounds(org.commcare.dalvik.R.drawable.ab_icon,0,0,0);
			iconBearer.setCompoundDrawablePadding(this.getResources().getDimensionPixelSize(org.commcare.dalvik.R.dimen.title_logo_pad));
			
			
			HorizontalScrollView scroller = new HorizontalScrollView(activity);
			scroller.addView(layout, new HorizontalScrollView.LayoutParams(HorizontalScrollView.LayoutParams.WRAP_CONTENT,HorizontalScrollView.LayoutParams.MATCH_PARENT));
			scroller.setFillViewport(true);
			
			return scroller;
		}
		
		private int addElementToTitle(LayoutInflater inflater, RelativeLayout title, String element, int type, int peer, OnClickListener action) {
			int newViewId = org.commcare.dalvik.R.id.component_title_breadcrumb_text + title.getChildCount() + 1;
			if(element != null) {
				View titleBreadcrumb = inflater.inflate(type, title, true);
				
				TextView text = (TextView)titleBreadcrumb.findViewById(org.commcare.dalvik.R.id.component_title_breadcrumb_text);
				
				if(action != null && isTopNavEnabled) {
					text.setOnClickListener(action);
				}
				
				text.setText(element);
				//Is there a "random ID" or something we can use for this?
				text.setId(newViewId);
				
				if(peer != -1) {
					View peerView = title.findViewById(peer);
					
					RelativeLayout.LayoutParams layout = (RelativeLayout.LayoutParams)peerView.getLayoutParams();
					layout.addRule(RelativeLayout.RIGHT_OF, newViewId);
				}
				return newViewId;
			}
			return -1;
		}
}
