package org.commcare.android.view;

import org.commcare.android.adapters.EntityDetailPagerAdapter;
import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;

import android.annotation.SuppressLint;
import android.content.Context;
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

public class TabbedDetailView extends RelativeLayout {
	private FragmentActivity mContext;
	
	private LinearLayout mMenu;
	private EntityDetailPagerAdapter mEntityDetailPagerAdapter;
	private ViewPager mViewPager;
	
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
	
	public void setRoot(ViewGroup root) {
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.tabbed_detail_view, root, true);

		mMenu = (LinearLayout) root.findViewById(R.id.tabbed_detail_menu);
		mViewPager = (ViewPager) root.findViewById(R.id.tabbed_detail_pager);

        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			
			@Override
			public void onPageSelected(int position) { markSelectedTab(position); }
			
			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) { }
			
			@Override
			public void onPageScrollStateChanged(int arg0) { }

		});
	}
	
	public void setDetail(Detail detail) {
        Detail[] details = detail.getDetails();
        if (details.length > 0) {
	        LinearLayout.LayoutParams fillLayout = new LinearLayout.LayoutParams(
	        	LinearLayout.LayoutParams.WRAP_CONTENT, 
	        	LinearLayout.LayoutParams.WRAP_CONTENT, 
	        	10f / details.length
	        );

	        for (Detail d : details) {
	        	String form = d.getTitleForm();
	        	if (form == null) {
	        		form = "";
	        	}
	        	String title = d.getTitle().evaluate();
	        	OnClickListener listener = new OnClickListener() {
					@Override
					public void onClick(View v) {
						int index = ((ViewGroup) v.getParent()).indexOfChild(v);
						mViewPager.setCurrentItem(index, true);
						markSelectedTab(index);
					}
	        	};
	        	
	        	// Create either TextView or ImageView for tab
	        	View view;
	        	if (form.equals(MediaUtil.FORM_IMAGE)) {
	        		view = new ImageView(mContext);
	        		((ImageView) view).setImageBitmap(ViewUtil.inflateDisplayImage(mContext, title));
	        	}
	        	else {
	        		view = new TextView(mContext);
		        	((TextView) view).setText(title);
		        	((TextView) view).setTextSize(getResources().getDimension(R.dimen.interactive_font_size));
		        	((TextView) view).setGravity(Gravity.CENTER);
	        	}
        		view.setClickable(true);
        		view.setOnClickListener(listener);
       			view.setBackgroundDrawable(getResources().getDrawable(R.drawable.title_neutral_tab_vertical));
		        mMenu.addView(view, fillLayout);	        		
	        }
	        markSelectedTab(0);
	        mMenu.setVisibility(View.VISIBLE);
        }
        else {
        	mMenu.setVisibility(View.GONE);
        }
	}
	
	/*
     * Get form list from database and insert into view.
	 */
	public void refresh(Detail detail, int index, boolean hasDetailCalloutListener) {
        mEntityDetailPagerAdapter = new EntityDetailPagerAdapter(mContext.getSupportFragmentManager(), detail, index, hasDetailCalloutListener);
        mViewPager.setAdapter(mEntityDetailPagerAdapter);
        markSelectedTab(0);
	}

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
