package org.commcare.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.commcare.adapters.EntityDetailPagerAdapter;
import org.commcare.adapters.ListItemViewStriper;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.utils.AndroidUtil;
import org.javarosa.core.model.instance.TreeReference;

import java.util.Objects;

/**
 * Widget that combines a ViewPager with a set of page titles styled to look like tabs.
 * User can navigate either by swiping through pages or by tapping the tabs.
 *
 * @author jschweers
 */
public class TabbedDetailView extends RelativeLayout {
    private AppCompatActivity mContext;
    private TabLayout mTabLayout;
    private ViewPager2 mViewPager;

    private int mEvenColor;
    private int mOddColor;

    public TabbedDetailView(Context context) {
        super(context);
    }

    public TabbedDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode()) return;
        mContext = (AppCompatActivity) context;

        loadViewConfig(context, attrs);
    }

    private void loadViewConfig(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.TabbedDetailView);
        int[] defaults = AndroidUtil.getThemeColorIDs(context, new int[]{R.attr.detail_even_row_color, R.attr.detail_odd_row_color});

        mEvenColor = typedArray.getColor(R.styleable.TabbedDetailView_even_row_color, defaults[0]);
        mOddColor = typedArray.getColor(R.styleable.TabbedDetailView_odd_row_color, defaults[1]);
        typedArray.recycle();
    }

    @SuppressLint("NewApi")
    public TabbedDetailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = (AppCompatActivity) context;
    }

    /*
     * Attach this view to a layout.
     */
    public void setRoot(ViewGroup root) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        inflater.inflate(R.layout.tabbed_detail_view, root, true);

        mViewPager = root.findViewById(R.id.tabbed_detail_pager);
        mViewPager.setId(AndroidUtil.generateViewId());
        mTabLayout = root.findViewById(R.id.tab_layout);
    }

    /**
     * Get form list from database and insert into view.
     */
    public void refresh(Detail detail, TreeReference reference, int index) {
        EntityDetailPagerAdapter entityDetailPagerAdapter = new EntityDetailPagerAdapter(mContext.getSupportFragmentManager(), mContext.getLifecycle(), detail, index, reference, new ListItemViewStriper(this.mOddColor, this.mEvenColor));
        mViewPager.setAdapter(entityDetailPagerAdapter);

        if (detail.isCompound()) {
            new TabLayoutMediator(mTabLayout, mViewPager,
                    (tab, position) -> tab.setText(entityDetailPagerAdapter.getPageTitle(position)))
                    .attach();
        } else {
            mTabLayout.setVisibility(GONE);
        }
    }

    public int getCurrentTab() {
        return mViewPager.getCurrentItem();
    }

    public int getTabCount() {
        if (mViewPager.getAdapter() == null) {
            return 0;
        }
        return mViewPager.getAdapter().getItemCount();
    }
}
