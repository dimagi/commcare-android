/**
 * 
 */
package org.commcare.android.view;

import java.util.Date;

import org.commcare.android.R;
import org.commcare.android.util.DotsEditListener;
import org.commcare.android.util.DotsData.DotsBox;
import org.commcare.android.util.DotsData.DotsDay;
import org.commcare.android.util.DotsData.MedStatus;
import org.javarosa.core.model.utils.DateUtils;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.RadioGroup.OnCheckedChangeListener;

/**
 * @author ctsims
 *
 */
public class DotsDetailView {
	
	private final static String[][] labels = new String[][] {
			new String[] {"Dose"},
			new String[] {"AM Dose", "PM Dose"},
			new String[] {"AM Dose", "Afternoon Dose", "PM Dose"},
			new String[] {"AM Dose", "Afternoon Dose", "PM Dose", "Evening Dose"}
	};
	
	RadioGroup[] groups;
	CheckBox[] selfReported;
	EditText[] missedName;
	DotsDay day;
	int index;
	
	public DotsDetailView() {
		
	}

	public View LoadDotsDetailView(Context context, DotsDay day, int index, Date date, final DotsEditListener listener) {
		this.day = day;
		this.index = index;
		View view = View.inflate(context, R.layout.dots_detail, null);

		TextView title = (TextView)view.findViewById(R.id.dots_detail_title);
		
		title.setText("DOTS Details for " + DateUtils.formatDate(date, DateUtils.FORMAT_HUMAN_READABLE_SHORT));
		
		LinearLayout container = (LinearLayout)view.findViewById(R.id.dots_content_frame);
		
		container.removeAllViews();
		
		String[] titles = labels[day.boxes().length - 1];
		groups = new RadioGroup[day.boxes().length];
		selfReported = new CheckBox[day.boxes().length];
		missedName = new EditText[day.boxes().length];
		
		for(int i = 0; i < day.boxes().length; ++i) {
			DotsBox box = day.boxes()[i];
			View details = View.inflate(context, R.layout.dotsentry, null);
			TextView timeView = (TextView)details.findViewById(R.id.text_time);
			timeView.setText(titles[i]);
			details.setPadding(0, 20, 0,0);
			
			final View missingDetails = details.findViewById(R.id.missed_details);
			missedName[i] = (EditText)details.findViewById(R.id.text_missed);
			
			groups[i] = (RadioGroup)details.findViewById(R.id.dose_group);
			selfReported[i] = (CheckBox)details.findViewById(R.id.cbx_self_reported);
			if(box.selfreported()) {
				selfReported[i].setChecked(true);
			}
			
			int id = -1;
			
			switch(box.status()) {
				case empty:
					id = R.id.radio_all;
					break;
				case partial:
					id = R.id.radio_some;
					missedName[i].setText(box.missedMeds());
					missingDetails.setVisibility(View.VISIBLE);
					break;
				case full:
					id = R.id.radio_none;
					break;
				case unchecked:
					id = R.id.radio_unchecked;
					break;
			}
			if(id != -1) {
				groups[i].check(id);
			}

			
			groups[i].setOnCheckedChangeListener(new OnCheckedChangeListener() {

				public void onCheckedChanged(RadioGroup group, int checkedId) {
					if(checkedId == R.id.radio_some) {
						missingDetails.setVisibility(View.VISIBLE);
					} else {
						missingDetails.setVisibility(View.GONE);
					}
				}
				
			});
			
			container.addView(details, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
		}
		
		Button ok = (Button)view.findViewById(R.id.btn_dots_detail_ok);
		Button cancel = (Button)view.findViewById(R.id.btn_dots_detail_cancel);
		
		ok.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				listener.dayEdited(DotsDetailView.this.index, DotsDetailView.this.getDay());
			}
			
		});
		
		cancel.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				listener.cancelDayEdit();
			}
			
		});
		
		return view;
	}
	
	public DotsDay getDay() {
		DotsBox[] boxes = new DotsBox[day.boxes().length];
		for(int i =0 ; i < day.boxes().length ; ++i) {
			MedStatus status = MedStatus.unchecked;
			String meds = null;
			switch(groups[i].getCheckedRadioButtonId()) {
				case R.id.radio_all:
					status= MedStatus.empty;
					break;
				case R.id.radio_some:
					status = MedStatus.partial;
					meds = missedName[i].getText().toString();
					break;
				case R.id.radio_none:
					status = MedStatus.full;
					break;
				case R.id.radio_unchecked:
					status = MedStatus.unchecked;
					break;
			}
			boxes[i] = new DotsBox(status,selfReported[i].isChecked(), meds);
		}
		return new DotsDay(boxes);
	}

}
