package org.commcare.android.view;

import org.commcare.android.adapters.EntityDetailPagerAdapter;
import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DisplayUnit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Widget that combines a ViewPager with a set of page titles styled to look like tabs.
 * User can navigate either by swiping through pages or by tapping the tabs.
 * @author jschweers
 *
 */
public class TabbedDetailView extends RelativeLayout {
    private FragmentActivity mContext;
    
    private LinearLayout mMenu;
    private EntityDetailPagerAdapter mEntityDetailPagerAdapter;
    private ViewPager mViewPager;
    private View mViewPagerWrapper;
    
    public TabbedDetailView(Context context) {
        super(context);
        mContext = (FragmentActivity) context;
    }

    public TabbedDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
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
    public void setDetail(Detail detail) {
        Detail[] details = detail.getDetails();

        LinearLayout.LayoutParams pagerLayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = 0;
        int menuVisibility = View.GONE;
        int backgroundColor = Color.TRANSPARENT;

        if (details.length > 0) {
            mMenu.setWeightSum(details.length);
            LinearLayout.LayoutParams fillLayout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
            );

            for (Detail d : details) {
                OnClickListener listener = new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int index = ((ViewGroup) v.getParent()).indexOfChild(v);
                        mViewPager.setCurrentItem(index, true);
                        markSelectedTab(index);
                    }
                };
                
                // Create TextImageAudioView for tab
                TextImageAudioView view = new TextImageAudioView(mContext);
                DisplayUnit title = d.getTitle();
                view.setAVT(title.getText().evaluate(), title.getAudioURI(), title.getImageURI());
                view.setGravity(Gravity.CENTER);
                view.setClickable(true);
                view.setOnClickListener(listener);
                view.setBackgroundDrawable(getResources().getDrawable(R.drawable.title_neutral_tab_vertical));
                mMenu.addView(view, fillLayout);                    
            }
            markSelectedTab(0);
            menuVisibility = View.VISIBLE;
            backgroundColor = mContext.getResources().getColor(R.color.yellow_green);
            margin = (int) getResources().getDimension(R.dimen.spacer);
            pagerLayout.setMargins(0, margin, margin, margin);
        }

        mMenu.setVisibility(menuVisibility);
        mViewPagerWrapper.setBackgroundColor(backgroundColor);
        pagerLayout.setMargins(0, margin, margin, margin);
        mViewPager.setLayoutParams(pagerLayout);
    }
    
    /*
     * Get form list from database and insert into view.
     */
    public void refresh(Detail detail, int index, boolean hasDetailCalloutListener) {
        mEntityDetailPagerAdapter = new EntityDetailPagerAdapter(mContext.getSupportFragmentManager(), detail, index, hasDetailCalloutListener);
        mViewPager.setAdapter(mEntityDetailPagerAdapter);
        markSelectedTab(0);
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
    
}
