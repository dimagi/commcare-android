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
import org.commcare.android.util.DotsData.ReportType;
import org.javarosa.core.model.utils.DateUtils;
import org.odk.collect.android.widgets.TriggerWidget;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * @author ctsims
 *
 */
public class DotsDetailView {
	
	public final static String[][] labels = new String[][] {
			new String[] {"Dose"},
			new String[] {"Morning", "Evening"},
			new String[] {"Morning", "Noon", "Evening"},
			new String[] {"Morning", "Noon", "Evening", "Bedtime"}
	};
	
	public final static String[] regimens = new String[] { "Non-ART", "ART"};  
	
	//RadioGroup[] groups;
	View[] groups; 
	CheckBox[] selfReported;
	EditText[] missedName;
	DotsDay day;
	int index;
	int dose;
	
	public DotsDetailView() {
		
	}

	public View LoadDotsDetailView(Context context, DotsDay day, int index, Date date, int dose, final DotsEditListener listener) {
		this.day = day;
		this.index = index;
		this.dose = dose;
		
		View view = View.inflate(context, R.layout.dots_detail, null);

		TextView title = (TextView)view.findViewById(R.id.dots_detail_title);
		
		title.setText("DOTS Details for " + DateUtils.formatDate(date, DateUtils.FORMAT_HUMAN_READABLE_SHORT));
		
		LinearLayout container = (LinearLayout)view.findViewById(R.id.dots_content_frame);
		
		container.removeAllViews();
		
		//groups = new RadioGroup[day.boxes().length];
		groups = new View[day.boxes().length];
		selfReported = new CheckBox[day.boxes().length];
		missedName = new EditText[day.boxes().length];
		
		int[] regimenIndices = day.getRegIndexes(dose);
		
		for(int i = 0; i < day.boxes().length; ++i) {
			int subIndex = regimenIndices[i];
			if(subIndex == -1) {
				continue;
			}
			DotsBox box = day.boxes()[i][subIndex];
			final View details = View.inflate(context, R.layout.compact_dot_entry, null);
			groups[i] = details;
			TextView timeView = (TextView)details.findViewById(R.id.text_time);
			timeView.setText(regimens[i] + ": " + labels[day.getMaxReg() -1 ][dose]);
			details.setPadding(0, 0, 0,0);
			
			final View missingDetails = details.findViewById(R.id.missed_details);
			missedName[i] = (EditText)details.findViewById(R.id.text_missed);
			
			//groups[i] = (RadioGroup)details.findViewById(R.id.dose_group);
			//selfReported[i] = (CheckBox)details.findViewById(R.id.cbx_self_reported);
			
			int type = R.id.tbt_pillbox; 
			
			switch(box.reportType()) {
			case self:
				type = R.id.tbt_self;
				break;
			case pillbox:
				type = R.id.tbt_pillbox;
				break;
			case direct:
				type = R.id.tbt_direct;
				break;
			}
			
			ToggleButton selectedReportType = (ToggleButton)details.findViewById(type);
			selectedReportType.setChecked(true);
						
			int checked = -1;
			
			switch(box.status()) {
				case empty:
					checked = R.id.radio_all;
					break;
				case partial:
					checked = R.id.radio_some;
					missedName[i].setText(box.missedMeds());
					missingDetails.setVisibility(View.VISIBLE);
					break;
				case full:
					checked = R.id.radio_none;
					break;
				case unchecked:
					checked = R.id.radio_unchecked;
					break;
			}
			
			ToggleButton checkedToggle = (ToggleButton)details.findViewById(checked);
			checkedToggle.setChecked(true);
			
			//set up listeners
			final int[] ids = new int[] {R.id.radio_all, R.id.radio_some, R.id.radio_unchecked, R.id.radio_none};
			for(int id : ids) {
				ToggleButton toggle = (ToggleButton)details.findViewById(id);
				toggle.setOnClickListener(new View.OnClickListener() {
		            public void onClick(View v) {
		            	for(int id : ids) {
		            		if(v.getId() == id) {
		            			((ToggleButton)v).setChecked(true);
		            		} else {
		            			ToggleButton toggle = (ToggleButton)details.findViewById(id);
		            			toggle.setChecked(false);
		            		}
		            		
		            		if(v.getId() == R.id.radio_some) {
								missingDetails.setVisibility(View.VISIBLE);
		    				} else {
		    					missingDetails.setVisibility(View.GONE);
		    				}
		            	}
		            }
		        });
			}
			
			//set up listeners
			final int[] typeids = new int[] {R.id.tbt_direct, R.id.tbt_pillbox, R.id.tbt_self};
			for(int id : typeids) {
				ToggleButton toggle = (ToggleButton)details.findViewById(id);
				toggle.setOnClickListener(new View.OnClickListener() {
		            public void onClick(View v) {
		            	for(int id : typeids) {
		            		if(v.getId() == id) {
		            			((ToggleButton)v).setChecked(true);
		            		} else {
		            			ToggleButton toggle = (ToggleButton)details.findViewById(id);
		            			toggle.setChecked(false);
		            		}
		            	}
		            }
		        });
			}
			
			if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.FILL_PARENT);
				params.leftMargin = 10;
				params.rightMargin = 10;
				params.weight = (float)(1f / day.boxes().length);
				container.addView(details, params);
				container.setOrientation(LinearLayout.HORIZONTAL);
			} else {
				container.addView(details, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
			}
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
				listener.cancelDoseEdit();
			}
			
		});
		
		return view;
	}
	
	public DotsDay getDay() {
		
		int[] regIndices = day.getRegIndexes(dose);
		DotsBox[] newBoxes = new DotsBox[regIndices.length];
		
		
		//Now go fill in the new data
		for(int i =0 ; i < regIndices.length ; ++i) {
			if(regIndices[i] == -1) {
				newBoxes[i] = null;
				continue;
			}
			
			int checkedButton = -1;
			//Retrieve the selected value
			int[] ids = new int[] {R.id.radio_all, R.id.radio_some, R.id.radio_unchecked, R.id.radio_none};
			for(int id : ids) {
				ToggleButton button = (ToggleButton)groups[i].findViewById(id);
				if(button.isChecked()) {
					checkedButton = id;
				}
			}
			
			int reportType = -1;
			//Retrieve the selected value
			int[] reportids = new int[] {R.id.tbt_direct, R.id.tbt_pillbox, R.id.tbt_self};
			for(int id : reportids) {
				ToggleButton button = (ToggleButton)groups[i].findViewById(id);
				if(button.isChecked()) {
					reportType = id;
				}
			}

			
			MedStatus status = MedStatus.unchecked;
			String meds = null;
			
			switch(checkedButton) {
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
			
			ReportType type = ReportType.pillbox;
			
			switch(reportType) {
				case R.id.tbt_direct:
					type = ReportType.direct;
					break;
				case R.id.tbt_pillbox:
					type = ReportType.pillbox;
					break;
				case R.id.tbt_self:
					type = ReportType.self;
					break;
			}
			
			newBoxes[i] = new DotsBox(status,type, meds);
		}
		
		return day.updateDose(dose, newBoxes);
	}
}
