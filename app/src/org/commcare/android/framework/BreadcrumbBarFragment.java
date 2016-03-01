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
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import org.commcare.activities.CommCareSetupActivity;
import org.commcare.activities.FormRecordListActivity;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.logging.AndroidLogger;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.SessionStateUninitException;
import org.commcare.views.GridEntityView;
import org.commcare.views.TabbedDetailView;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.NoLocalizedTextException;

import java.util.Vector;

/**
 * @author ctsims
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BreadcrumbBarFragment extends Fragment {

    private TabbedDetailView mInternalDetailView = null;
    private View tile;

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
        boolean breadCrumbsEnabled = !DeveloperPreferences.isActionBarEnabled();

        ActionBar actionBar = activity.getActionBar();

        if (!breadCrumbsEnabled) {
            configureSimpleNav(activity, actionBar);
        } else {
            attachBreadcrumbBar(activity, actionBar);
        }

        this.tile = findAndLoadCaseTile(activity);
    }

    private void configureSimpleNav(Activity activity, ActionBar actionBar) {
        boolean showNav = true;
        if (activity instanceof CommCareActivity) {
            showNav = ((CommCareActivity)activity).isBackEnabled();
        }

        if (showNav) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        actionBar.setDisplayShowTitleEnabled(true);
        String title = getBestTitle(activity);
        actionBar.setTitle(title);
    }

    private void attachBreadcrumbBar(Activity activity, ActionBar actionBar) {
        //make sure we're in the right mode
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);

        //We need to get the amount that each item should "bleed" over to the left, and move the whole widget that
        //many pixels. This replicates the "overlap" space that each piece of the bar has on the next piece for
        //the left-most element.
        int buffer = Math.round(activity.getResources().getDimension(R.dimen.title_round_bleed));
        LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        p.leftMargin = buffer;

        activity.setTitle("");
        actionBar.setDisplayShowHomeEnabled(false);
    }

    private static void expand(Activity activity, final View v) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        if (activity instanceof CommCareActivity) {
            ((CommCareActivity)activity).setMainScreenBlocked(true);
        }

        int specHeight = MeasureSpec.makeMeasureSpec(display.getHeight(), MeasureSpec.AT_MOST);

        v.measure(LayoutParams.MATCH_PARENT, specHeight);
        final int targetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)v.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)v.getLayoutParams();

                if (interpolatedTime == 1) {
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

    private static void collapse(Activity activity, final View v) {
        if (activity instanceof CommCareActivity) {
            ((CommCareActivity)activity).setMainScreenBlocked(false);
        }
        final int initialHeight = v.getMeasuredHeight();

        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)v.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        lp.height = initialHeight;

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)v.getLayoutParams();
                    lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 1);
                    lp.height = 0;
                } else {
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

    private View findAndLoadCaseTile(final Activity activity) {
        final View holder = LayoutInflater.from(activity).inflate(R.layout.com_tile_holder, null);
        final Pair<View, TreeReference> tileData = this.loadTile(activity);
        View tile = tileData == null ? null : tileData.first;
        if (tile == null) {
            return null;
        }

        final String inlineDetail = (String)tile.getTag();

        ((ViewGroup)holder.findViewById(R.id.com_tile_holder_frame)).addView(tile, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        final ImageButton infoButton = ((ImageButton)holder.findViewById(R.id.com_tile_holder_btn_open));

        if (inlineDetail == null) {
            infoButton.setVisibility(View.GONE);
        }

        OnClickListener toggleButtonClickListener = new OnClickListener() {

            private boolean isClosed = true;

            @Override
            public void onClick(View v) {
                if (isClosed) {
                    if (mInternalDetailView == null) {
                        mInternalDetailView = (TabbedDetailView)holder.findViewById(R.id.com_tile_holder_detail_frame);
                        mInternalDetailView.setRoot(mInternalDetailView);

                        AndroidSessionWrapper asw = CommCareApplication._().getCurrentSessionWrapper();
                        CommCareSession session = asw.getSession();

                        Detail detail = session.getDetail(inlineDetail);
                        mInternalDetailView.showMenu();
                        mInternalDetailView.refresh(detail, tileData.second, 0);
                    }
                    expand(activity, holder.findViewById(R.id.com_tile_holder_detail_master));
                    infoButton.setImageResource(R.drawable.icon_info_fill_brandbg);
                    isClosed = false;
                } else {
                    collapse(activity, holder.findViewById(R.id.com_tile_holder_detail_master));
                    infoButton.setImageResource(R.drawable.icon_info_outline_brandbg);
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
        for (int i = v.size() - 1; i >= 0; i--) {
            StackFrameStep step = v.elementAt(i);

            if (SessionFrame.STATE_DATUM_VAL.equals(step.getType())) {
                //Only add steps which have a tile.
                SessionDatum d = asw.getSession().findDatumDefinition(step.getId());
                if (d != null && d.getPersistentDetail() != null) {
                    stepToFrame = step;
                }
            }
        }

        Pair<View, TreeReference> tile = buildContextTile(stepToFrame, asw);
        //some contexts may provide a tile that isn't really part of the current session's stack
        if (tile == null && activity instanceof CommCareActivity) {
            Pair<Detail, TreeReference> entityContext = ((CommCareActivity)activity).requestEntityContext();
            if (entityContext != null) {
                tile = buildContextTile(entityContext.first, entityContext.second, asw);
            }
        }
        return tile;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (tile != null) {
            ViewGroup vg = (ViewGroup)this.getActivity().findViewById(R.id.universal_frame_tile);
            //Check whether the view group is available. If so, this activity is a frame tile host 
            if (vg != null) {
                if (tile.getParent() != null) {
                    ((ViewGroup)tile.getParent()).removeView(tile);
                }
                vg.addView(tile, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                //this doesn't really make it over well
                mInternalDetailView = null;
            }
        }
    }

    private static String getBestTitle(Activity activity) {
        String bestTitle = getBestTitleHelper();
        return defaultTitle(bestTitle, activity);
    }

    /**
     * Unlike the main header, subheaders should not fall back to a default title
     */
    public static String getBestSubHeaderTitle() {
        return getBestTitleHelper();
    }

    private static String getBestTitleHelper() {
        AndroidSessionWrapper asw;

        try {
            asw = CommCareApplication._().getCurrentSessionWrapper();
        } catch (SessionStateUninitException e) {
            return null;
        }

        CommCareSession session = asw.getSession();

        String[] stepTitles;
        try {
            stepTitles = session.getHeaderTitles();
        } catch (NoLocalizedTextException e) {
            // localization resources may not be installed while in the middle
            // of an update, so default to a generic title
            return null;
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
        return bestTitle;
    }

    private static String defaultTitle(String currentTitle, Activity activity) {
        if (activity instanceof CommCareSetupActivity) {
            return "CommCare";
        }
        if (currentTitle == null || "".equals(currentTitle)) {
            currentTitle = CommCareActivity.getTopLevelTitleName(activity);
        }
        if (currentTitle == null || "".equals(currentTitle)) {
            currentTitle = "CommCare";
        }
        if (activity instanceof FormRecordListActivity) {
            currentTitle = currentTitle + " - " + ((FormRecordListActivity)activity).getActivityTitle();
        }
        return currentTitle;
    }

    private Pair<View, TreeReference> buildContextTile(StackFrameStep stepToFrame, AndroidSessionWrapper asw) {
        if (stepToFrame == null) {
            return null;
        }

        //check to make sure we can look up this child
        SessionDatum d = asw.getSession().findDatumDefinition(stepToFrame.getId());
        if (d == null || d.getPersistentDetail() == null) {
            return null;
        }

        //Make sure there is a valid reference to the entity we can build
        Detail detail = asw.getSession().getDetail(d.getPersistentDetail());

        EvaluationContext ec = asw.getEvaluationContext();

        TreeReference ref = d.getEntityFromID(ec, stepToFrame.getValue());
        if (ref == null) {
            return null;
        }

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
}
