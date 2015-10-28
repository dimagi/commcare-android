package org.commcare.android.framework;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.AndroidUtil;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.SessionStateUninitException;
import org.commcare.android.view.GridEntityView;
import org.commcare.android.view.TabbedDetailView;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareSetupActivity;
import org.commcare.dalvik.activities.FormRecordListActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.NoLocalizedTextException;

import java.util.Vector;

/**
 * @author ctsims
 *
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BreadcrumbBarFragment extends Fragment {
    
    private boolean isTopNavEnabled = false;
    private int localIdPart = -1;
     
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
     
      
    boolean breadCrumbsEnabled = true;
    /**
     * Hold a reference to the parent Activity so we can report the task's
     * current progress and results. The Android framework will pass us a
     * reference to the newly created Activity after each configuration change.
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Activity) {
            refresh((Activity)context);
        } else {
            Logger.log(AndroidLogger.SOFT_ASSERT, "Unable to attach breadcrumb bar fragment");
        }
    }

    public void refresh(Activity activity) {
        breadCrumbsEnabled = !DeveloperPreferences.isActionBarEnabled();

        ActionBar actionBar = activity.getActionBar();

        if(!breadCrumbsEnabled) {
            configureSimpleNav(activity, actionBar);
        } else {
            attachBreadcrumbBar(activity, actionBar);
        }

        this.tile = findAndLoadCaseTile(activity);
    }
        
    private void configureSimpleNav(Activity activity, ActionBar actionBar) {
        String title = null;
        String local = null;
        if(activity instanceof CommCareActivity) {
            local = ((CommCareActivity)activity).getActivityTitle();
        }

        if(title == null) {
            title = getBestTitle(activity);
        }

        boolean showNav = true;
        if(activity instanceof CommCareActivity) {
            showNav = ((CommCareActivity)activity).isBackEnabled();
        }
        if(showNav) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        actionBar.setDisplayShowTitleEnabled(true);
        
        actionBar.setTitle(title);
                
  }



    private void attachBreadcrumbBar(Activity activity, ActionBar actionBar) {
        String title = null;
        
        //make sure we're in the right mode
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        
        if(activity instanceof CommCareActivity) {
            title = ((CommCareActivity)activity).getActivityTitle();
            isTopNavEnabled = ((CommCareActivity)activity).isTopNavEnabled();
        }
        
        //We need to get the amount that each item should "bleed" over to the left, and move the whole widget that
        //many pixels. This replicates the "overlap" space that each piece of the bar has on the next piece for
        //the left-most element.
        int buffer = Math.round(activity.getResources().getDimension(R.dimen.title_round_bleed));
        LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        p.leftMargin = buffer;

        activity.setTitle("");
        actionBar.setDisplayShowHomeEnabled(false);
    }
    
    public static void expand(Activity activity, final View v) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        
        int specHeight = MeasureSpec.makeMeasureSpec(display.getHeight(), MeasureSpec.AT_MOST);

        
        v.measure(LayoutParams.MATCH_PARENT, specHeight);
        final int targetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)v.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation()
        {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)v.getLayoutParams();
                
                if(interpolatedTime == 1) {
                    lp.height = 0;
                    lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 1);
                } else {       
                        lp.height = (int)(targetHeight * interpolatedTime);
                }
                
                v.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int)(targetHeight / v.getContext().getResources().getDisplayMetrics().density) * 2);
        v.startAnimation(a);
    }

    public static void collapse(final View v, final Runnable postExecuteLambda) {
        final int initialHeight = v.getMeasuredHeight();
        
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)v.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        lp.height = initialHeight;

        Animation a = new Animation()
        {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if(interpolatedTime == 1){
                    v.setVisibility(View.GONE);
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)v.getLayoutParams();
                    lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 1);
                    lp.height = 0;
                    postExecuteLambda.run();
                }else{
                    v.getLayoutParams().height = initialHeight - (int)(initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int)(initialHeight / v.getContext().getResources().getDisplayMetrics().density) * 2);
        v.startAnimation(a);
    }
    
    private TabbedDetailView mInternalDetailView = null;
    
    private View findAndLoadCaseTile(final Activity activity) {
        final View holder = LayoutInflater.from(activity).inflate(R.layout.com_tile_holder, null);
        final Pair<View, TreeReference> tileData = this.loadTile(activity);
        View tile = tileData == null ? null : tileData.first;
        if(tile == null) { return null;}
        
        final String inlineDetail = (String)tile.getTag();

        ((ViewGroup)holder.findViewById(R.id.com_tile_holder_frame)).addView(tile, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        final ImageButton infoButton = ((ImageButton)holder.findViewById(R.id.com_tile_holder_btn_open));

        if(inlineDetail == null) {
            infoButton.setVisibility(View.GONE);
        }

        OnClickListener toggleButtonClickListener = new OnClickListener() {

            private boolean isClosed = true;

            @Override
            public void onClick(View v) {
                if(isClosed){
                    if(mInternalDetailView == null ) {
                        mInternalDetailView = (TabbedDetailView)holder.findViewById(R.id.com_tile_holder_detail_frame);
                        mInternalDetailView.setRoot(mInternalDetailView);

                        AndroidSessionWrapper asw = CommCareApplication._().getCurrentSessionWrapper();
                        CommCareSession session = asw.getSession();

                        NodeEntityFactory factory = new NodeEntityFactory(session.getDetail(inlineDetail), session.getEvaluationContext(new CommCareInstanceInitializer(session)));
                        Detail detail = factory.getDetail();
                        mInternalDetailView.setDetail(detail);

                        mInternalDetailView.refresh(factory.getDetail(), tileData.second,0, false);
                    }
                    infoButton.setImageResource(R.drawable.icon_info_fill_brandbg);
                    expand(activity, holder.findViewById(R.id.com_tile_holder_detail_master));
                    isClosed = false;
                } else {
                    //collapses view
                    infoButton.setImageResource(R.drawable.icon_info_outline_brandbg);
                    collapse(holder.findViewById(R.id.com_tile_holder_detail_master), new Runnable() {
                        @Override
                        public void run() {
                        }
                    });
                    isClosed = true;
                }
            }
        };

        infoButton.setOnClickListener(toggleButtonClickListener);
        
        return holder;
    }


    private Pair<View, TreeReference> loadTile(Activity activity) {
        AndroidSessionWrapper asw;
        try {
            asw = CommCareApplication._().getCurrentSessionWrapper();
        } catch (SessionStateUninitException e) {
            return null;
        }

        CommCareSession session = asw.getSession();

        StackFrameStep stepToFrame = null;
        Vector<StackFrameStep> v = session.getFrame().getSteps();

        //So we need to work our way backwards through each "step" we've taken, since our RelativeLayout
        //displays the Z-Order b insertion (so items added later are always "on top" of items added earlier
        for(int i = v.size() -1 ; i >= 0; i--){
            StackFrameStep step = v.elementAt(i);

            if(SessionFrame.STATE_DATUM_VAL.equals(step.getType())) {
                //Only add steps which have a tile.
                SessionDatum d = asw.getSession().findDatumDefinition(step.getId());
                if(d != null && d.getPersistentDetail() != null) {
                    stepToFrame = step;
                }
            }
        }

        Pair<View, TreeReference> tile = buildContextTile(stepToFrame, asw);
        //some contexts may provide a tile that isn't really part of the current session's stack
        if(tile == null && activity instanceof CommCareActivity) {
            Pair<Detail, TreeReference> entityContext = ((CommCareActivity)activity).requestEntityContext();
            if(entityContext != null) {
                tile = buildContextTile(entityContext.first, entityContext.second, asw);
            }
        }
        return tile;
    }


    @Override
    public void onResume() {
        super.onResume();
        if(tile != null) {
            ViewGroup vg = (ViewGroup)this.getActivity().findViewById(R.id.universal_frame_tile);
            //Check whether the view group is available. If so, this activity is a frame tile host 
            if(vg != null) {
                if(((ViewGroup) tile.getParent()) != null) {
                    ((ViewGroup) tile.getParent()).removeView(tile);
                }
                vg.addView(tile, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                //this doesn't really make it over well
                mInternalDetailView = null;
            }
        }
        if(this.getActivity() instanceof CommCareActivity) {
            String title = ((CommCareActivity)this.getActivity()).getActivityTitle();
            
            if(title != null) {
            
                if(breadCrumbsEnabled) {
                    //This part can change more dynamically
                    if(localIdPart != -1 ) {
                        TextView text = (TextView)this.getActivity().getActionBar().getCustomView().findViewById(localIdPart);
                        if(text != null) {
                            text.setText(title);
                        }
                    }
                }
            }
        }
    }
    
    public static String getBestTitle(Activity activity) {
        AndroidSessionWrapper asw;

        try {
            asw = CommCareApplication._().getCurrentSessionWrapper();
        } catch (SessionStateUninitException e) {
            return defaultTitle(null, activity);
        }

        CommCareSession session = asw.getSession();

        String[] stepTitles;
        try {
            stepTitles = session.getHeaderTitles();
        } catch (NoLocalizedTextException e) {
            // localization resources may not be installed while in the middle
            // of an update, so default to a generic title
            return defaultTitle(null, activity);
        }

        Vector<StackFrameStep> v = session.getFrame().getSteps();

        //So we need to work our way backwards through each "step" we've taken, since our RelativeLayout
        //displays the Z-Order b insertion (so items added later are always "on top" of items added earlier
        String bestTitle = null;
        for (int i = v.size() - 1; i >= 0; i--) {
            if (bestTitle != null) {
                break;
            }
            StackFrameStep step = v.elementAt(i);

            if (!SessionFrame.STATE_DATUM_VAL.equals(step.getType())) {
                bestTitle = stepTitles[i];
            }
        }

        return defaultTitle(bestTitle, activity);
    }

    private static String defaultTitle(String currentTitle, Activity activity) {
        if (activity instanceof CommCareSetupActivity) {
            return "CommCare";
        }
        if(currentTitle == null || "".equals(currentTitle)) {
            currentTitle = CommCareActivity.getTopLevelTitleName(activity);
        }
        if(currentTitle == null || "".equals(currentTitle)) {
            currentTitle = "CommCare";
        }
        if (activity instanceof FormRecordListActivity) {
            currentTitle = currentTitle + " - " + ((FormRecordListActivity)activity).getActivityTitle();
        }
        return currentTitle;
    }


    /**
     * Get the breadcrumb bar view
     * 
     * Sunsetting this soon.
     */
        public View getTitleView(final Activity activity, String local) {
            
            RelativeLayout layout = new RelativeLayout(activity);
            HorizontalScrollView scroller = new HorizontalScrollView(activity) {
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
                AndroidSessionWrapper asw = CommCareApplication._().getCurrentSessionWrapper();
                CommCareSession session = asw.getSession();

                stepTitles = session.getHeaderTitles();
                
                Vector<StackFrameStep> v = session.getFrame().getSteps();
                
                //So we need to work our way backwards through each "step" we've taken, since our RelativeLayout
                //displays the Z-Order b insertion (so items added later are always "on top" of items added earlier
                for(int i = v.size() -1 ; i >= 0; i--){
                    StackFrameStep step = v.elementAt(i);
                    
                    //Keep track of how many "steps" (or choices made by the user/platform) have happened.
                    final int currentStep = i;
                    
                    //How many "steps" have already been made to get to the step we're making a view for?
                    final int currentStepSize = v.size();
                
                    OnClickListener stepBackListener = new OnClickListener() {

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
                            } catch (SessionStateUninitException e) {
                                
                            }
                        }
                        
                    };

                    try {
                        //It the current step was selecting a nodeset value...
                    if(SessionFrame.STATE_DATUM_VAL.equals(step.getType())) {
                        //Haaack. We should replace this with a generalizable "What do you refer to your detail by", but for now this is 90% of cases
                        if(step.getId() != null && step.getId().contains("case_id")) {
                            ACase foundCase = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class).getRecordForValue(ACase.INDEX_CASE_ID, step.getValue());
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
            } catch(SessionStateUninitException e) {
            }
            
            //Finally add the "top level" breadcrumb that represents the application's home. 
            addElementToTitle(li, layout, topLevel, org.commcare.dalvik.R.layout.component_title_breadcrumb, currentId, new OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    try{
                        CommCareApplication._().getCurrentSession().clearAllState();
                    } catch (SessionStateUninitException e) {

                    }
                    
                    activity.finish();
                }
                
            });
            
            //Add the app icon
            TextView iconBearer = ((TextView)layout.getChildAt(layout.getChildCount() - 1));
            
            iconBearer.setCompoundDrawablesWithIntrinsicBounds(R.drawable.icon_app_white,0,0,0);
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
        
        View tile;
        
        private Pair<View, TreeReference> buildContextTile(StackFrameStep stepToFrame, AndroidSessionWrapper asw) {
            if(stepToFrame == null) { return null; }
            
            //check to make sure we can look up this child
            SessionDatum d = asw.getSession().findDatumDefinition(stepToFrame.getId());
            if(d == null || d.getPersistentDetail() == null) { return null; }
            
            //Make sure there is a valid reference to the entity we can build 
            Detail detail = asw.getSession().getDetail(d.getPersistentDetail());
            
            EvaluationContext ec = asw.getEvaluationContext();
            
            TreeReference ref = d.getEntityFromID(ec, stepToFrame.getValue());
            if(ref == null) { return null; }
            
            Pair<View, TreeReference> r = buildContextTile(detail, ref, asw);
            r.first.setTag(d.getInlineDetail());
            return r;
        }

        private Pair<View, TreeReference> buildContextTile(Detail detail, TreeReference ref, AndroidSessionWrapper asw) {
            NodeEntityFactory nef = new NodeEntityFactory(detail, asw.getEvaluationContext());
            
            Entity entity = nef.getEntity(ref);

            Log.v("DEBUG-v", "Creating new GridEntityView for text header text");
            GridEntityView tile = new GridEntityView(this.getActivity(), detail, entity);
            int[] textColor = AndroidUtil.getThemeColorIDs(getActivity(), new int[]{R.attr.drawer_pulldown_text_color, R.attr.menu_tile_title_text_color});
            tile.setTextColor(textColor[0]);
            tile.setTitleTextColor(textColor[1]);
            return Pair.create(((View)tile), ref);
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
