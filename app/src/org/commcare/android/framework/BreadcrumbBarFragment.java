/**
 * 
 */
package org.commcare.android.framework;

import java.util.Vector;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.SessionFrame;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
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
	private int localIdPart = -1;
	 
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
		    
		    String activityTitle = null;
		    
		    if(activity instanceof CommCareActivity) {
		    	activityTitle = ((CommCareActivity)activity).getActivityTitle();
		    	isTopNavEnabled = ((CommCareActivity)activity).isTopNavEnabled();
		    }
		    
		    ActionBar actionBar = activity.getActionBar();
		    
		    //We need to get the amount that each item should "bleed" over to the left, and move the whole widget that
		    //many pixels. This replicates the "overlap" space that each piece of the bar has on the next piece for
		    //the left-most element.
		    int buffer = Math.round(activity.getResources().getDimension(R.dimen.title_round_bleed));
		    LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
		    p.leftMargin = buffer;
		    
		    actionBar.setCustomView(getTitleView(activity, activityTitle), p);
		    activity.setTitle("");
		    actionBar.setDisplayShowHomeEnabled(false);
	  }
	  
		
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onResume()
	 */
	@Override
	public void onResume() {
		super.onResume();
		if(this.getActivity() instanceof CommCareActivity) {
			String title = ((CommCareActivity)this.getActivity()).getActivityTitle();
			
			//This part can change more dynamically
			if(localIdPart != -1 && title != null) {
				TextView text = (TextView)this.getActivity().getActionBar().getCustomView().findViewById(localIdPart);
				text.setText(title);
			}
		}
	}


		public View getTitleView(final Activity activity, String local) {
			RelativeLayout layout = new RelativeLayout(activity);
			HorizontalScrollView scroller = new HorizontalScrollView(activity) {
				/*
				 * (non-Javadoc)
				 * @see android.widget.HorizontalScrollView#onLayout(boolean, int, int, int, int)
				 */
				@Override
				protected void onLayout(boolean changed, int l, int t, int r, int b) {
				    super.onLayout(changed, l, t, r, b);
				    this.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
				}
			};
			scroller.setHorizontalScrollBarEnabled(false);
			scroller.addView(layout, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT,HorizontalScrollView.LayoutParams.MATCH_PARENT));
			scroller.setFillViewport(true);
			
			RelativeLayout fullTopBar = new RelativeLayout(activity);
			RelativeLayout.LayoutParams topBarParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
			topBarParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			
			
			//layout.setGravity(Gravity.CENTER_VERTICAL);
			
			// we have to do this walk backwards, actually
			
			String topLevel = CommCareActivity.getTopLevelTitleName(activity);
			LayoutInflater li = activity.getLayoutInflater();
			
			int currentId = -1;
			
			//We don't actually want this one to look the same
			int newId = org.commcare.dalvik.R.id.component_title_breadcrumb_text + layout.getChildCount() + 1;			
			if(local != null) {
				localIdPart = newId;
				View titleBreadcrumb = li.inflate(org.commcare.dalvik.R.layout.component_title_uncrumb, fullTopBar, true);
				
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
				topBarParams.addRule(RelativeLayout.LEFT_OF, newId);
			}
			
			fullTopBar.addView(scroller, topBarParams);

			
			if(newId != -1) { currentId = newId;}
			
			String[] stepTitles = new String[0];
			try {
				stepTitles = CommCareApplication._().getCurrentSession().getHeaderTitles();
				
				Vector<String[]> v = CommCareApplication._().getCurrentSession().getFrame().getSteps();
				
				//So we need to work our way backwards through each "step" we've taken, since our RelativeLayout
				//displays the Z-Order b insertion (so items added later are always "on top" of items added earlier
				for(int i = v.size() -1 ; i >= 0; i--){
					String[] step = v.elementAt(i);
					
					//Keep track of how many "steps" (or choices made by the user/platform) have happened.
					final int currentStep = i;
					
					//How many "steps" have already been made to get to the step we're making a view for?
					final int currentStepSize = v.size();
				
					OnClickListener stepBackListener = new OnClickListener() {

						/*
						 * (non-Javadoc)
						 * @see android.view.View.OnClickListener#onClick(android.view.View)
						 */
						@Override
						public void onClick(View arg0) {
							
							int stepsToTake = currentStepSize - currentStep - 1;
							
							try{
								//Try to take stepsToTake steps "Back" 
								for(int i = 0 ; i < stepsToTake ; i++) {
									CommCareApplication._().getCurrentSession().stepBack();
									
									//We need to check the current size, because sometimes a step back will end up taking
									//two (if a value is computed instead of selected)
									int currentStepSize = CommCareApplication._().getCurrentSession().getFrame().getSteps().size();
									
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
						
						//It the current step was selecting a nodeset value... 
					if(SessionFrame.STATE_DATUM_VAL.equals(step[0])) {
						
						//Haaack. We should replace this with a generalizable "What do you refer to your detail by", but for now this is 90% of cases
						if(step[1] != null && step[1].contains("case_id")) {
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
			
			//Finally add the "top level" breadcrumb that represents the application's home. 
			addElementToTitle(li, layout, topLevel, org.commcare.dalvik.R.layout.component_title_breadcrumb, currentId, new OnClickListener() {

				/*
				 * (non-Javadoc)
				 * @see android.view.View.OnClickListener#onClick(android.view.View)
				 */
				@Override
				public void onClick(View arg0) {
					try{
						CommCareApplication._().getCurrentSession().clearAllState();
					} catch(SessionUnavailableException sue) {
						
					}
					
					activity.finish();
				}
				
			});
			
			//Add the app icon
			TextView iconBearer = ((TextView)layout.getChildAt(layout.getChildCount() - 1));
			
			iconBearer.setCompoundDrawablesWithIntrinsicBounds(org.commcare.dalvik.R.drawable.ab_icon,0,0,0);
			iconBearer.setCompoundDrawablePadding(this.getResources().getDimensionPixelSize(org.commcare.dalvik.R.dimen.title_logo_pad));
			
			//Add an "Anchor" view to the left hand side of the bar. The relative layout doesn't work unless
			//there's a view that isn't relative to the other views. The anchor is explicitly relative to
			//only the parent layout.
			currentId = currentId + 2343241;			
			View anchor = new FrameLayout(activity);
			anchor.setId(currentId);
			
			// The Anchor should be as wide as the bleed off the screen. This is necessary, because otherwise the layout 
			// starts _off_ the screen to the left (due to the margin on the last item here). We'll shift the parent 
			// layout itself back over so it doesn't look awkward.
			int buffer = Math.round(activity.getResources().getDimension(R.dimen.title_round_depth));
			layout.addView(anchor, buffer, LayoutParams.MATCH_PARENT);			
			((RelativeLayout.LayoutParams)iconBearer.getLayoutParams()).addRule(RelativeLayout.RIGHT_OF, currentId);
			
			return fullTopBar;
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
				
				text.getLayoutParams().width = LayoutParams.WRAP_CONTENT;
				
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
