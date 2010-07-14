/**
 * 
 */
package org.commcare.android.view;

import java.util.Calendar;

import org.commcare.android.R;
import org.commcare.android.util.DotsData;
import org.commcare.android.util.DotsEditListener;
import org.commcare.android.util.DotsData.DotsBox;
import org.commcare.android.util.DotsData.DotsDay;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

/**
 * @author ctsims
 *
 */
public class DotsHomeView extends RelativeLayout {
	
	DotsData data;
	DotsEditListener listener;
	private static final String[] dayArray = new String[] {"Su","Mo","Tu","We","Th","Fr","Sa"};
	
	
	public static final int TABLE_LENGTH = 7;
	int offset;

	public DotsHomeView(Context context, DotsData data, DotsEditListener listener, int offset) {
		super(context);
		this.data = data; 
		this.listener = listener;
		this.offset = offset;
		refresh();
	}
	
	private void refresh() {
		this.removeAllViews();
		
		ImageView earlier = new ImageView(this.getContext());
		earlier.setImageResource(R.drawable.prev_arrow);
		earlier.setScaleType(ScaleType.CENTER_INSIDE);
		earlier.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				listener.shiftWeek(-1);
			}
		});
		
		ImageView later = new ImageView(this.getContext());
		later.setImageResource(R.drawable.next_arrow);
		later.setScaleType(ScaleType.CENTER_INSIDE);
		later.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				listener.shiftWeek(1);
			}
		});
		
		TableLayout table = new TableLayout(this.getContext());
		
		int days = data.days().length;
		int rows = (int)Math.ceil(days / TABLE_LENGTH);
		
		int displayRow = (rows -1) - offset;
		
		LayoutParams earlierParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		earlierParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		earlierParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		this.addView(earlier);
		
		LayoutParams laterParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		laterParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		laterParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		this.addView(later, laterParams);
		
		if(!(displayRow > 0 )) {
			earlier.setVisibility(INVISIBLE);
		} 
		if(!(rows > displayRow +1)) {
			earlier.setVisibility(INVISIBLE);
		}
		
		int lowerbound = displayRow * TABLE_LENGTH;
		int upperbound = Math.min((displayRow + 1) * TABLE_LENGTH, days);
		
		table.setShrinkAllColumns(true);
		table.setStretchAllColumns(true);

		//TableRow arrowrow = new TableRow(this.getContext());
		TableRow toprow = new TableRow(this.getContext());
		TableRow bottomrow = new TableRow(this.getContext());
		
		Calendar c = Calendar.getInstance();
		c.setTime(data.anchor());
		c.roll(Calendar.DAY_OF_YEAR, -(data.days().length - lowerbound -1 ));
		
		
		int[] topindices;
		int[] bottomindices = null;
		
		switch(data.days()[0].boxes().length) {
			case 1:
				topindices = new int[] {0};
				break;
			case 2:
				topindices = new int[] {0};
				bottomindices = new int[] {1};
				break;
			case 3:
				topindices = new int[] {0,1};
				bottomindices = new int[] {2};
				break;
			case 4:
				topindices = new int[] {0,1};
				bottomindices = new int[] {2,3};
				break;
			default:
				topindices = new int[data.days()[0].boxes().length];
				for(int j = 0 ; j < topindices.length ; ++j){ 
					topindices[j] = j;
				}
		}
		
		for(int i = lowerbound ; i < upperbound ; ++i) {
			
//			if(i == lowerbound) {
//				//arrowrow.addView(new Button(this.getContext()));
//				arrowrow.addView(earlier);
//			} else if( i == upperbound -1) {
//				//arrowrow.addView(new Button(this.getContext()));
//				arrowrow.addView(later);
//			} else {
//				//Just an empty placeholder
//				//arrowrow.addView(new ImageView(this.getContext()), new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
//				View empty = new Button(this.getContext());
//				empty.setVisibility(INVISIBLE);
//				arrowrow.addView(empty);
//			}
			
			DotsDay day = data.days()[i];
			
			View topview = getDayView(c, day, i, topindices);
			toprow.addView(topview);
			
			if(bottomindices != null) {
				View bottomview = getDayView(c, day, i, bottomindices);
				bottomrow.addView(bottomview);
			}
			
			c.roll(Calendar.DAY_OF_YEAR, 1);
		}
		
//		toprow.setGravity(Gravity.CENTER_VERTICAL);
//		bottomrow.setGravity(Gravity.CENTER_VERTICAL);
		
//		table.addView(arrowrow);
		table.addView(toprow);
		if(bottomindices != null) {
			table.addView(bottomrow);
		}
		
		if(displayRow > 0 ) {
			earlier.setVisibility(VISIBLE);
		}  else {
			earlier.setVisibility(INVISIBLE);
		}
		if(rows > displayRow +1) {
			later.setVisibility(VISIBLE);
		}  else {
			later.setVisibility(INVISIBLE);
		}
		
		Button done = new Button(this.getContext());
		done.setText("Finished");
		done.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				listener.doneWithWeek();
			}
			
		});
		

    	table.setGravity(Gravity.CENTER_VERTICAL);
    	LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
    	//params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
    	params.addRule(RelativeLayout.BELOW, earlier.getId());
    	params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
    	params.addRule(RelativeLayout.ABOVE, done.getId());
    	
		this.addView(table, params);
		params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
		this.addView(done, params);
	}

	
	private View getDayView(Calendar c, DotsDay d, final int dayIndex, final int[] boxes) {
		View dayView = View.inflate(this.getContext(), R.layout.dotsday, null);
		//day.setOnClickListener()
		TextView date = (TextView)dayView.findViewById(R.id.text_date);
		TextView dow = (TextView)dayView.findViewById(R.id.text_dow);
		//LinearLayout doses = (LinearLayout)dayView.findViewById(R.id.dose_status);
		TableLayout table = (TableLayout)dayView.findViewById(R.id.dose_table);
		table.setPadding(0,0,2,0);
		table.setShrinkAllColumns(true);
		
		TableRow doses = (TableRow)dayView.findViewById(R.id.dose_status);
		TableRow selfReported = (TableRow)dayView.findViewById(R.id.self_report_row);
		
		dow.setText(dayArray[c.get(Calendar.DAY_OF_WEEK) -1]);
		date.setText((c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH));
		
		doses.removeAllViews();
		for(int i = 0 ; i < boxes.length ; ++i) {
			DotsBox box = d.boxes()[boxes[i]];
			ImageView status = new ImageView(this.getContext());
			status.setPadding(0,0,1,0);
			switch(box.status()) {
			case full:
				status.setImageResource(R.drawable.redx);
				break;
			case partial:
				status.setImageResource(R.drawable.blues);
				break;
			case empty:
				status.setImageResource(R.drawable.checkmark);
				break;
			case unchecked:
				status.setImageResource(R.drawable.blueq);
				break;
			}
				
			doses.addView(status);
			
			ImageView selfReport = new ImageView(this.getContext());
			selfReport.setPadding(0,3,1,0);
			selfReport.setImageResource(R.drawable.greencircle);
			if(!box.selfreported()) {
				selfReport.setVisibility(INVISIBLE);
			}
			selfReported.addView(selfReport);
		}
		
		dayView.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				Rect hitRect = new Rect();
				if(v.getParent() instanceof View) {
					v.getHitRect(hitRect);
					View parent = (View)v.getParent();
					DotsHomeView.this.offsetDescendantRectToMyCoords(parent, hitRect);
					listener.editDotsDay(dayIndex,hitRect, boxes);
				} else{
					hitRect = new Rect(0,0,v.getWidth(), v.getHeight());
					DotsHomeView.this.offsetDescendantRectToMyCoords(v, hitRect);
					listener.editDotsDay(dayIndex,hitRect, boxes);
				}
			}
			
		});
		
		return dayView;
	}
}
