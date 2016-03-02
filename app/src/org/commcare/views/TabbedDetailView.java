package org.commcare.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.commcare.adapters.EntityDetailPagerAdapter;
import org.commcare.adapters.ListItemViewStriper;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.utils.AndroidUtil;
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
    private ViewPager mViewPager;

    private View mViewPageTabStrip;

    private int mEvenColor;
    private int mOddColor;

    public TabbedDetailView(Context context) {
        super(context);
    }

    public TabbedDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode()) return;
        mContext = (FragmentActivity)context;

        loadViewConfig(context, attrs);
    }

    private void loadViewConfig(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.TabbedDetailView);
        int[] defaults = AndroidUtil.getThemeColorIDs(context, new int[]{R.attr.detail_even_row_color, R.attr.detail_odd_row_color});

        mEvenColor = typedArray.getColor(R.styleable.TabbedDetailView_even_row_color, defaults[0]);
        mOddColor = typedArray.getColor(R.styleable.TabbedDetailView_odd_row_color, defaults[1]);
    }

    @SuppressLint("NewApi")
    public TabbedDetailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = (FragmentActivity)context;
    }

    /*
     * Attach this view to a layout.
     */
    public void setRoot(ViewGroup root) {
        LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        inflater.inflate(R.layout.tabbed_detail_view, root, true);

        mMenu = (LinearLayout)root.findViewById(R.id.tabbed_detail_menu);
        mViewPager = (ViewPager)root.findViewById(R.id.tabbed_detail_pager);
        mViewPager.setId(AndroidUtil.generateViewId());

        mViewPageTabStrip = root.findViewById(R.id.pager_tab_strip);

        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(int position) {
                markSelectedTab(position);
            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {
            }

            @Override
            public void onPageScrollStateChanged(int arg0) {
            }

        });
    }

    public void showMenu() {
        mMenu.setVisibility(VISIBLE);
    }

    /**
     * Get form list from database and insert into view.
     */
    public void refresh(Detail detail, TreeReference reference, int index) {
        EntityDetailPagerAdapter entityDetailPagerAdapter =
                new EntityDetailPagerAdapter(mContext.getSupportFragmentManager(), detail, index, reference,
                        new ListItemViewStriper(this.mOddColor, this.mEvenColor));
        mViewPager.setAdapter(entityDetailPagerAdapter);
        if (!detail.isCompound()) {
            if (mViewPageTabStrip != null) {
                mViewPageTabStrip.setVisibility(GONE);
            }
        }
    }

    /**
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

    public int getCurrentTab() {
        return mViewPager.getCurrentItem();
    }

    public int getTabCount() {
        return mViewPager.getAdapter().getCount();
    }
}
