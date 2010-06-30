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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
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
	
	
	int tableLength = 7;

	public DotsHomeView(Context context, DotsData data, DotsEditListener listener) {
		super(context);
		this.data = data; 
		this.listener = listener;
		refresh();
	}
	
	private void refresh() {
		this.removeAllViews();
		
		TextView title = new TextView(this.getContext());
		title.setText("Weekly DOTS Data");
		title.setTextAppearance(this.getContext(), android.R.style.TextAppearance_Medium);
		this.addView(title, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP));
		
		TableLayout table = new TableLayout(this.getContext());
		
		int days = data.days().length;
		int rows = (int)Math.floor(days / tableLength);
		
		table.setShrinkAllColumns(true);
		table.setStretchAllColumns(true);
		TableRow row = new TableRow(this.getContext());
		
		Calendar c = Calendar.getInstance();
		c.setTime(data.anchor());
		c.roll(Calendar.DAY_OF_YEAR, -(data.days().length -1));
		
		for(int i = 0 ; i < data.days().length ; ++i) {
		    if(i > tableLength -1 && i % tableLength == 0 ) {
		    	table.addView(row);
				row = new TableRow(this.getContext());
				rows--;
		    }
			DotsDay day = data.days()[i];
			final int dayIndex = i; 
			View view = getDayView(c, day);
			view.setOnClickListener(new OnClickListener() {

				public void onClick(View v) {
					listener.editDotsDay(dayIndex);
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
				
			doses.addView(status);
		}
		
		return dayView;
		
	}
}
