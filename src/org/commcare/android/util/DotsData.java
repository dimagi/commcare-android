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
	
	public static enum MedStatus {
		unchecked,
		full,
		empty,
		partial;
	}
	
	DotsDay[] days;
	
	public static final class DotsBox {
		
		MedStatus status;
		String missedMeds;
		
		public DotsBox(MedStatus status) {
			this.status = status;
		}
		
		public DotsBox(MedStatus status, String missedMeds) {
			this.status = status;
			this.missedMeds = missedMeds;
		}
		
		public MedStatus status() {
			return status;
		}
		
		public String missedMeds() {
			return missedMeds;
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
				boxes[j] = new DotsBox(MedStatus.valueOf(box), missed);
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
			if(i - difference >= 0 && i - difference < this.days.length) {
				newDays[i] = this.days[i - difference];
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
			boxes[j] = new DotsBox(MedStatus.unchecked);
		}
		return boxes;
	}
}
