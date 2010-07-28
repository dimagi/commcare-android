/**
 * 
 */
package org.commcare.android.util;

import java.util.Date;

import org.javarosa.core.model.utils.DateUtils;

/**
 * @author ctsims
 *
 */
public class DotsData {
	Date anchor;
	
	int regimen;
	//private static final String SELF_REPORT_FLAG = "MCSR";
	
	public static enum MedStatus {
		unchecked,
		full,
		empty,
		partial;
	}
	
	public static enum ReportType {
		direct,
		pillbox,
		self
	}
	
	DotsDay[] days;
	
	public static final class DotsBox {
		
		MedStatus status;
		String missedMeds;
		ReportType type;
		
		public DotsBox(MedStatus status, ReportType type) {
			this(status, type, null);
		}
		
		public DotsBox(MedStatus status, ReportType type, String missedMeds) {
			this.status = status;
			this.missedMeds = missedMeds;
			this.type = type;
		}
		
		public MedStatus status() {
			return status;
		}
		
		public String missedMeds() {
			return missedMeds;
		}
		
		public ReportType reportType() {
			return type;
		}
	}
	
	public static final class DotsDay {
		
		DotsBox[] boxes;
		
		public DotsDay(DotsBox[] boxes) {
			this.boxes = boxes;
		}
		
		public DotsBox[] boxes() {
			return boxes;
		}
		
		public String serialize() {
			String serial = "";
			for(int j = 0 ; j < this.boxes.length ; ++j) {
				serial += this.boxes[j].status.toString();
				serial+=this.boxes[j].type.toString();
				if(this.boxes[j].missedMeds != null) {
					serial += " " + this.boxes[j].missedMeds;
				}
				if(j + 1 < this.boxes.length) { 
					serial += ";";
				}
			}
			return serial;
		}
		
		public static DotsDay deserialize(String day) {
			String[] boxStrings = day.split(";");
			
			DotsBox[] boxes = new DotsBox[boxStrings.length];
			
			for(int j = 0 ; j < boxes.length ; ++j) {
				String box = boxStrings[j];
				String missed = null;
				if(box.contains(" ")){
					missed = box.substring(box.indexOf(" "), box.length());
					box = box.substring(0, box.indexOf(" "));
				}
				ReportType type = ReportType.pillbox;
				//This is unforgivably bad, but we'll ignore that
				//since we're switching to json anyway.
				if(box.endsWith(ReportType.direct.toString())) {
					box = box.substring(0, box.indexOf(ReportType.direct.toString()));
					type = ReportType.direct;
				} else if(box.endsWith(ReportType.pillbox.toString())) {
					box = box.substring(0, box.indexOf(ReportType.pillbox.toString()));
					type = ReportType.pillbox;
				} else if(box.endsWith(ReportType.self.toString())) {
					box = box.substring(0, box.indexOf(ReportType.self.toString()));
					type = ReportType.self;
				}
				
				boxes[j] = new DotsBox(MedStatus.valueOf(box), type, missed);
			}
			
			return new DotsDay(boxes);

		}
	}
	
	public DotsDay[] days() {
		return days;
	}
	
	public Date anchor() {
		return anchor;
	}
	

	private DotsData(Date anchor, int regType, DotsDay[] days) {
		this.anchor = anchor;
		this.regimen = regType;
		this.days = days;
	}
	
	
	public void recenter(Date newAnchor) {
		int difference = DateUtils.dateDiff(anchor, newAnchor);
		if(difference == 0) {
			return;
		}
		DotsDay[] newDays = new DotsDay[this.days.length];
		for(int i = 0; i < newDays.length; ++i) {
			if(difference + i >= 0 && difference + i < this.days.length) {
				newDays[i] = this.days[difference + i];
			} else {
				newDays[i] = new DotsDay(emptyBoxes(this.regimen));
			}
		}
		this.anchor = newAnchor;
		this.days = newDays;
	}
	
	public String SerializeDotsData() {
		
		String serialized = anchor.toGMTString() + "|";
		serialized += String.valueOf(regimen);
		
		for(int i = 0 ; i < days.length ; ++i) {
			serialized += "|";
			serialized += days[i].serialize();
		}
		return serialized;
	}
	
	public static DotsData DeserializeDotsData(String dots) {
		String[] data = dots.split("\\|");
		
		Date anchor = new Date(Date.parse(data[0]));
		
		int regType = Integer.parseInt(data[1]);
		
		int numDays = data.length - 2;
		
		DotsDay[] days = new DotsDay[numDays];
		
		for(int i = 0 ; i < numDays ; ++i) {
			String day = data[i + 2];
			days[i] = DotsDay.deserialize(day);
		}
		
		return new DotsData(anchor, regType, days);
	}
	
	public static DotsData CreateDotsData(int regType, Date anchor) {
		DotsDay[] days = new DotsDay[21];
		for(int i = 0 ; i <  days.length ; ++i ) {
			days[i] = new DotsDay(emptyBoxes(regType));
		}
		
		DotsData data = new DotsData(anchor, regType, days);
		return data;
	}
	
	public static DotsBox[] emptyBoxes(int length) {
		DotsBox[] boxes = new DotsBox[length];
		for(int j = 0 ; j <  boxes.length ; ++j ) {
			boxes[j] = new DotsBox(MedStatus.unchecked, ReportType.pillbox);
		}
		return boxes;
	}
}
