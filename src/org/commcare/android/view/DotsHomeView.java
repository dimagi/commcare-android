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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class DotsHomeView extends FrameLayout {
	
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
		
		TextView title = new TextView(this.getContext());
		title.setText("Weekly DOTS Data");
		title.setTextAppearance(this.getContext(), android.R.style.TextAppearance_Medium);
		//this.addView(title, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP));
		ImageView earlier = new ImageView(this.getContext());
		earlier.setImageResource(R.drawable.prev_arrow);
		earlier.setPadding(0,20,0,0);
		
		
		ImageView later = new ImageView(this.getContext());
		later.setImageResource(R.drawable.next_arrow);
		later.setPadding(0,20,0,0);
		
		TableLayout table = new TableLayout(this.getContext());
		
		int days = data.days().length;
		int rows = (int)Math.ceil(days / TABLE_LENGTH);
		
		int displayRow = (rows -1) - offset;
		
		if(displayRow > 0 ) {
			this.addView(earlier, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
		} 
		if(rows > displayRow +1) {
			this.addView(later, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP));
		}
		
		int lowerbound = displayRow * TABLE_LENGTH;
		int upperbound = Math.min((displayRow + 1) * TABLE_LENGTH, days);
		
		table.setShrinkAllColumns(true);
		table.setStretchAllColumns(true);
		TableRow row = new TableRow(this.getContext());
		
		Calendar c = Calendar.getInstance();
		c.setTime(data.anchor());
		c.roll(Calendar.DAY_OF_YEAR, -(data.days().length - lowerbound -1 ));
		
		for(int i = lowerbound ; i < upperbound ; ++i) {
//		    if(i > tableLength -1 && i % tableLength == 0 ) {
//		    	table.addView(row);
//				row = new TableRow(this.getContext());
//				rows--;
//		    }
			DotsDay day = data.days()[i];
			final int dayIndex = i; 
			View view = getDayView(c, day);
			view.setOnClickListener(new OnClickListener() {

				public void onClick(View v) {
					Rect hitRect = new Rect();
					if(v.getParent() instanceof View) {
						v.getHitRect(hitRect);
						View parent = (View)v.getParent();
						DotsHomeView.this.offsetDescendantRectToMyCoords(parent, hitRect);
						listener.editDotsDay(dayIndex,hitRect);
					} else{
						hitRect = new Rect(0,0,v.getWidth(), v.getHeight());
						DotsHomeView.this.offsetDescendantRectToMyCoords(v, hitRect);
						listener.editDotsDay(dayIndex,hitRect);
					}
				}
				
			});
			row.addView(view);
			
			c.roll(Calendar.DAY_OF_YEAR, 1);
		}
		
		if(rows != 0) {
			table.addView(row);
		}
		
    	table.setGravity(Gravity.CENTER_VERTICAL);
		this.addView(table, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
		
		Button done = new Button(this.getContext());
		done.setText("Finished");
		done.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				listener.doneWithWeek();
			}
			
		});
		this.addView(done, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
	}

	
	private View getDayView(Calendar c, DotsDay d) {
		View dayView = View.inflate(this.getContext(), R.layout.dotsday, null);
		//day.setOnClickListener()
		TextView date = (TextView)dayView.findViewById(R.id.text_date);
		TextView dow = (TextView)dayView.findViewById(R.id.text_dow);
		LinearLayout doses = (LinearLayout)dayView.findViewById(R.id.dose_status);
		//TableRow doses = (TableRow)dayView.findViewById(R.id.dose_status);
		
		dow.setText(dayArray[c.get(Calendar.DAY_OF_WEEK) -1]);
		date.setText(c.get(Calendar.MONTH) + "/" + c.get(Calendar.DAY_OF_MONTH));
		
		doses.removeAllViews();
		for(DotsBox box : d.boxes()) {
			TextView status = new TextView(this.getContext());
			status.setPadding(0,0,4,0);
			switch(box.status()) {
			case full:
				status.setText("J");
				break;
			case partial:
				status.setText("S");
				break;
			case empty:
				status.setText("X");
				break;
			case unchecked:
				status.setText("?");
				break;
			default:
				status.setText("E");
				break;
			}
				
			//doses.addView(status);
			
			ImageView image = new ImageView(this.getContext());
			image.setImageResource(R.drawable.checkmark);
			image.setPadding(0,0,2,0);
			doses.addView(image);
		}
		
		return dayView;
		
	}
}
