package org.commcare.android.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.commcare.android.adapters.EntityDetailAdapter;
import org.commcare.android.adapters.EntityDetailPagerAdapter;
import org.commcare.android.util.AndroidUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;

/**
 * Widget that combines a ViewPager with a set of page titles styled to look like tabs.
 * User can navigate either by swiping through pages or by tapping the tabs.
 *
 * @author jschweers
 */
public class TabbedDetailView extends RelativeLayout {
    private FragmentActivity mContext;
    
    private LinearLayout mMenu;
    private EntityDetailPagerAdapter mEntityDetailPagerAdapter;
    private ViewPager mViewPager;
    private View mViewPagerWrapper;

    private int mAlternateId = -1;

    public TabbedDetailView(Context context) {
        this(context, -1);
    }
    
    /**
     * Create a tabbed detail view with a specific root pager ID
     * (this is necessary in any context where multiple detail views
     * will be used at once)
     */
    public TabbedDetailView(Context context, int alternateId) {
        super(context);
        mContext = (FragmentActivity) context;
        this.mAlternateId = alternateId;
    }

    public TabbedDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if(isInEditMode()) return;
        mContext = (FragmentActivity) context;
    }
    
    @SuppressLint("NewApi")
    public TabbedDetailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = (FragmentActivity) context;
    }
    
    /*
     * Attach this view to a layout.
     */
    public void setRoot(ViewGroup root) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        inflater.inflate(R.layout.tabbed_detail_view, root, true);

        mMenu = (LinearLayout) root.findViewById(R.id.tabbed_detail_menu);
        mViewPager = (ViewPager) root.findViewById(R.id.tabbed_detail_pager);
        if(mAlternateId != -1) {
            mViewPager.setId(mAlternateId);
        }
        mViewPagerWrapper = root.findViewById(R.id.tabbed_detail_pager_wrapper);

        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            
            @Override
            public void onPageSelected(int position) { markSelectedTab(position); }
            
            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) { }
            
            @Override
            public void onPageScrollStateChanged(int arg0) { }

        });
    }
    
    /*
     * Populate view with content from given Detail.
     */
    public void setDetail(Detail detail) { mMenu.setVisibility(VISIBLE); }
    
    /*
     * Get form list from database and insert into view.
     */
    public void refresh(Detail detail, TreeReference reference, int index, boolean hasDetailCalloutListener) {
        mEntityDetailPagerAdapter = new EntityDetailPagerAdapter(mContext.getSupportFragmentManager(), detail, index, reference,
                hasDetailCalloutListener, new DefaultEDVModifier()
        );
        mViewPager.setAdapter(mEntityDetailPagerAdapter);
        if(!detail.isCompound()) {
            View pagerTabStrip = getRootView().findViewById(R.id.pager_tab_strip);
            if (pagerTabStrip != null) {
                pagerTabStrip.setVisibility(GONE);
            }
        }
    }

    /*
     * Style one tab as "selected".
     */
    private void markSelectedTab(int position) {
        if (mMenu.getChildCount() <= position) {
            return;
        }
        
        for (int i = 0; i < mMenu.getChildCount(); i++) {
            mMenu.getChildAt(i).setBackgroundDrawable(getResources().getDrawable(R.drawable.title_neutral_tab_vertical));
        }
        mMenu.getChildAt(position).setBackgroundDrawable(getResources().getDrawable(R.drawable.title_case_tab_vertical));
    }
    
    /**
     * Get the position of the current tab.
     * @return Zero-indexed integer
     */
    public int getCurrentTab() {
        return mViewPager.getCurrentItem();
    }
    
    /**
     * Get the number of tabs.
     * @return Integer
     */
    public int getTabCount() {
        return mViewPager.getAdapter().getCount();
    }

    //region Private classes

    @SuppressLint("ParcelCreator")
    private class DefaultEDVModifier implements EntityDetailAdapter.EntityDetailViewModifier, Parcelable {
        final int[] rowColors = AndroidUtil.getThemeColorIDs(getContext(),
                new int[]{R.attr.drawer_pulldown_even_row_color, R.attr.drawer_pulldown_odd_row_color});

        public DefaultEDVModifier() {
        }

        @Override
        public void modifyEntityDetailView(EntityDetailView edv) {
            edv.setOddEvenRowColors(rowColors[0], rowColors[1]);
        }

        @Override
        public int describeContents() {
            return rowColors[0] ^ rowColors[1];
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }
    }

    //endregion
}
