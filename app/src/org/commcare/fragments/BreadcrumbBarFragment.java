package org.commcare.fragments;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.activities.FormRecordListActivity;
import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.SessionStateUninitException;
import org.commcare.views.EntityViewTile;
import org.commcare.views.TabbedDetailView;
import org.commcare.views.UserfacingErrorHandling;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.NoLocalizedTextException;
import org.javarosa.xpath.XPathException;

import java.util.Vector;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBar.LayoutParams;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

/**
 * @author ctsims
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BreadcrumbBarFragment extends Fragment {

    private TabbedDetailView mInternalDetailView = null;
    private View tile;

    private final static String INLINE_TILE_COLLAPSED = "collapsed";
    private final static String INLINE_TILE_EXPANDED = "expanded";


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
        if (context instanceof AppCompatActivity) {
            refresh((AppCompatActivity)context);
        } else {
            Logger.log(LogTypes.TYPE_ERROR_WORKFLOW, "Unable to attach breadcrumb bar fragment");
        }
    }

    public void refresh(AppCompatActivity activity) {
        boolean breadCrumbsEnabled = !DeveloperPreferences.isActionBarEnabled();

        ActionBar actionBar = activity.getSupportActionBar();

        if (!breadCrumbsEnabled) {
            configureSimpleNav(activity, actionBar);
        } else {
            attachBreadcrumbBar(activity, actionBar);
        }

        try {
            this.tile = findAndLoadCaseTile(activity);
        } catch (XPathException xe) {
            UserfacingErrorHandling.logErrorAndShowDialog((CommCareActivity)getActivity(), xe, true);
        }
    }

    private void configureSimpleNav(AppCompatActivity activity, ActionBar actionBar) {
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

    private void attachBreadcrumbBar(AppCompatActivity activity, ActionBar actionBar) {
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

    private static void expand(AppCompatActivity activity, final View v) {
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

    private static void collapse(AppCompatActivity activity, final View v) {
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

    private View findAndLoadCaseTile(final AppCompatActivity activity) {
        final View holder = LayoutInflater.from(activity).inflate(R.layout.com_tile_holder, null);
        final Pair<View, TreeReference> tileData = this.loadTile(activity);
        if (tileData == null || tileData.first == null) {
            return null;
        }

        View tile = tileData.first;
        final String inlineDetail = (String)tile.getTag();
        ((ViewGroup)holder.findViewById(R.id.com_tile_holder_frame)).addView(tile, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        ImageButton infoButton = holder.findViewById(R.id.com_tile_holder_btn_open);
        if (inlineDetail == null) {
            infoButton.setVisibility(View.GONE);
        }

        holder.setTag(INLINE_TILE_COLLAPSED);

        infoButton.setOnClickListener(v -> {
            boolean isCollapsed = INLINE_TILE_COLLAPSED.equals(holder.getTag());
            if (isCollapsed) {
                expandInlineTile(activity, holder, tileData, inlineDetail);
            } else {
                collapseTileIfExpanded(activity);
            }
        });
        return holder;
    }

    public void expandInlineTile(AppCompatActivity activity, View holder,
                                 Pair<View, TreeReference> tileData,
                                 String inlineDetailId) {

        if (mInternalDetailView == null) {
            mInternalDetailView = holder.findViewById(R.id.com_tile_holder_detail_frame);
            mInternalDetailView.setRoot(mInternalDetailView);

            AndroidSessionWrapper asw = CommCareApplication.instance().getCurrentSessionWrapper();
            CommCareSession session = asw.getSession();

            Detail detail = session.getDetail(inlineDetailId);
            mInternalDetailView.showMenu();
            mInternalDetailView.refresh(detail, tileData.second, 0);
        }
        expand(activity, holder.findViewById(R.id.com_tile_holder_detail_master));

        ImageButton infoButton = holder.findViewById(R.id.com_tile_holder_btn_open);
        infoButton.setImageResource(R.drawable.icon_info_fill_brandbg);
        holder.setTag(INLINE_TILE_EXPANDED);
    }

    /**
     * Collapses the context tile currently display, if one exists and is expanded.
     *
     * Returns true if a tile exists and was expanded, false if no tile existed or if it was not
     * expanded.
     */
    public boolean collapseTileIfExpanded(AppCompatActivity activity) {
        View holder = tile;
        if (holder == null) {
            return false;
        }

        boolean isExpanded = INLINE_TILE_EXPANDED.equals(holder.getTag());
        if (!isExpanded) {
            return false;
        }

        collapse(activity, holder.findViewById(R.id.com_tile_holder_detail_master));

        ImageButton infoButton = holder.findViewById(R.id.com_tile_holder_btn_open);
        infoButton.setImageResource(R.drawable.icon_info_outline_brandbg);
        holder.setTag(INLINE_TILE_COLLAPSED);
        return true;
    }

    private Pair<View, TreeReference> loadTile(AppCompatActivity activity) {
        AndroidSessionWrapper asw;
        try {
            asw = CommCareApplication.instance().getCurrentSessionWrapper();
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
                EntityDatum entityDatum = asw.getSession().findDatumDefinition(step.getId());
                if (entityDatum != null && entityDatum.getPersistentDetail() != null) {
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
            ViewGroup vg = this.getActivity().findViewById(R.id.universal_frame_tile);
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

    private static String getBestTitle(AppCompatActivity activity) {
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
            asw = CommCareApplication.instance().getCurrentSessionWrapper();
        } catch (SessionStateUninitException e) {
            return null;
        }

        CommCareSession session = asw.getSession();

        String[] stepTitles;
        try {
            stepTitles = session.getHeaderTitles();
        } catch (NoLocalizedTextException | XPathException e) {
            // localization resources may not be installed while in the middle
            // of an update, so default to a generic title

            // Also Catch XPathExceptions here since we don't want to show the xpath error on app startup
            // and the errors here will be visible to the user when they go to the respective menu
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

    private static String defaultTitle(String currentTitle, AppCompatActivity activity) {
        if (activity instanceof CommCareSetupActivity) {
            return activity.getString(R.string.application_name);
        }
        if (currentTitle == null || "".equals(currentTitle)) {
            currentTitle = CommCareActivity.getTopLevelTitleName(activity);
        }
        if (currentTitle == null || "".equals(currentTitle)) {
            currentTitle = activity.getString(R.string.application_name);
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
        EntityDatum entityDatum = asw.getSession().findDatumDefinition(stepToFrame.getId());
        if (entityDatum == null || entityDatum.getPersistentDetail() == null) {
            return null;
        }

        //Make sure there is a valid reference to the entity we can build
        Detail detail = asw.getSession().getDetail(entityDatum.getPersistentDetail());

        EvaluationContext ec = asw.getEvaluationContext();

        TreeReference ref = entityDatum.getEntityFromID(ec, stepToFrame.getValue());
        if (ref == null) {
            return null;
        }

        Pair<View, TreeReference> r = buildContextTile(detail, ref, asw);
        r.first.setTag(entityDatum.getInlineDetail());
        return r;
    }

    private Pair<View, TreeReference> buildContextTile(Detail detail, TreeReference ref, AndroidSessionWrapper asw) {
        NodeEntityFactory nef = new NodeEntityFactory(detail, asw.getEvaluationContext());

        Entity entity = nef.getEntity(ref);

        Log.v("DEBUG-v", "Creating new GridEntityView for text header text");
        EntityViewTile tile = EntityViewTile.createTileForIndividualDisplay(getActivity(),
                detail, entity);
        int[] textColor = AndroidUtil.getThemeColorIDs(getActivity(),
                new int[]{R.attr.drawer_pulldown_text_color, R.attr.menu_tile_title_text_color});
        tile.setTextColor(textColor[0]);
        tile.setTitleTextColor(textColor[1]);
        return Pair.create(tile, ref);
    }
}
