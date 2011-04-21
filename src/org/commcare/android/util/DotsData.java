/**
 * 
 */
package org.commcare.android.util;

import java.util.Date;

import org.commcare.android.util.DotsData.DotsBox;
import org.commcare.android.util.DotsData.DotsDay;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * @author ctsims
 *
 */
public class DotsData {
	Date anchor;
	
	int[] regimens;
	
	DotsDay[] days;
	
	private static final int[][] equivM = new int[][] {
		new int[] {0,-1,-1,-1},
		new int[] {0,-1,1,-1},
		new int[] {0,1, 2,-1},
		new int[] {0,1,2, 3}
	};
	
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
				
		public static DotsBox deserialize(String box){
			try {
				return DotsBox.deserialize(new JSONArray(new JSONTokener(box)));
			}catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}
		
		public static DotsBox deserialize(JSONArray box) throws JSONException {
			String missed = null;
			if(box.length() > 2) {
				missed= box.getString(2);
			}
			
			String status = box.getString(0);
			String type = box.getString(1);
			return new DotsBox(MedStatus.valueOf(status), ReportType.valueOf(type), missed); 
		}
		
		public JSONArray serialize() {
			JSONArray ser = new JSONArray();
			ser.put(status.toString());
			ser.put(type.toString());
			if(missedMeds != null){
				ser.put(missedMeds);
			}
			return ser;
		}
	}
	
	public static final class DotsDay {
		
		DotsBox[][] boxes;
		
		public DotsDay(DotsBox[][] boxes) {
			this.boxes = boxes;
		}
		
		public DotsBox[][] boxes() {
			return boxes;
		}
		
		/**
		 * Whether this box contains the default observations for a 
		 * new day object. 
		 * 
		 * @return True if the day is indistinguishable from a default day,
		 * false otherwise
		 */
		public boolean isDefault() {
			for(int i = 0; i < boxes.length ; ++i) {
				for(int j = 0; j < boxes[i].length; ++j) {
					if(boxes[i][j].status != MedStatus.unchecked) {
						return false;
					}
				}
			}
			return true;
		}
		
		public JSONArray serialize() {
			JSONArray day = new JSONArray();
			for(int i = 0; i < boxes.length ; ++i) {
				JSONArray regimen = new JSONArray();
				for(int j = 0; j < boxes[i].length ; ++j) {
					regimen.put(boxes[i][j].serialize());
				}
				day.put(regimen);
			}
			return day;
		}
		
		public static DotsDay deserialize(String day) {
			try { 
				return deserialize(new JSONArray(new JSONTokener(day)));
			} catch(JSONException e) {
				throw new RuntimeException(e);
			}
		}
		
		public static DotsDay deserialize(JSONArray day) throws JSONException {
			DotsBox[][] fullDay = new DotsBox[day.length()][];
			for(int i = 0 ; i < day.length() ; ++i) {
				JSONArray regimen = day.getJSONArray(i);
				DotsBox[] boxes = new DotsBox[regimen.length()];
				for(int j = 0 ; j < regimen.length() ; ++j) {
					boxes[j] = DotsBox.deserialize(regimen.getJSONArray(j));
				}
				fullDay[i] = boxes;
			}
			return new DotsDay(fullDay);

		}

		public int getMaxReg() {
			return 4;
//			int max = 0;
//			for(DotsBox[] reg : boxes) {
//				if(reg.length > max) {
//					max = reg.length;
//				}
//			}
//			return max;
		}
		
		public int[] getRegIndexes(int index) {
			int max = this.getMaxReg();
			int[] retVal = new int[boxes.length];
			for(int i = 0 ; i < boxes.length ; ++i) {
				if(boxes[i].length == 0) {
					retVal[i] = -1;
				} else {
					retVal[i] = equivM[boxes[i].length -1 ][index];
				}
			}
			return retVal;
		}

		public DotsDay updateDose(int dose, DotsBox[] newboxes) {
			int[] indices = getRegIndexes(dose);
			for(int i = 0 ; i < boxes.length; ++i) {
				if(indices[i] == -1) {
					//Nothing to do
				} else {
					this.boxes[i][indices[i]] = newboxes[i];
				}
			}
			return this;
		}

		public MedStatus status() {
			MedStatus ret = null;
			for(int i = 0 ; i < boxes.length; ++i) {
				for(int j = 0 ; j < boxes[i].length; ++j) {
					DotsBox b = boxes[i][j];
					if(ret == null) {
						if(b.status != MedStatus.unchecked) {
							ret = MedStatus.empty;
						} else {
							ret = MedStatus.unchecked;
						}
					} else {
						if(ret == MedStatus.unchecked) {
							if(b.status != MedStatus.unchecked) {
								return MedStatus.partial;
							}
						}
						else if(ret == MedStatus.empty) {
							if(b.status == MedStatus.unchecked) {
								return MedStatus.partial;
							}
						}
					}
				}
			}
			if(ret == null) {
				return MedStatus.unchecked;
			} else {
				return ret;
			}
		}
	}
	
	public DotsDay[] days() {
		return days;
	}
	
	public Date anchor() {
		return anchor;
	}
	

	private DotsData(Date anchor, int[] regimens, DotsDay[] days) {
		this.anchor = anchor;
		this.regimens = regimens;
		this.days = days;
	}
	
	
	public int recenter(int[] regimens, Date newAnchor) {
		this.regimens = regimens;
		int difference = DateUtils.dateDiff(anchor, newAnchor);
		if(difference == 0) {
			return 0;
		}
		DotsDay[] newDays = new DotsDay[this.days.length];
		for(int i = 0; i < newDays.length; ++i) {
			if(difference + i >= 0 && difference + i < this.days.length) {
				newDays[i] = this.days[difference + i];
			} else {
				newDays[i] = new DotsDay(emptyBoxes(this.regimens));
			}
		}
		this.anchor = newAnchor;
		this.days = newDays;
		return difference;
	}
	
	public String SerializeDotsData() {
		
		try{
			JSONObject object = new JSONObject();
			object.put("anchor", anchor.toGMTString());
			JSONArray jRegs = new JSONArray();
			for(int i : regimens) {
				jRegs.put(i);
			}
			object.put("regimens", jRegs);
			
			JSONArray jDays = new JSONArray();
			for(DotsDay day : days) {
				jDays.put(day.serialize());
			}
			object.put("days", jDays);
			
			return object.toString();
		} catch(JSONException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static DotsData DeserializeDotsData(String dots) {
		
		try {
			JSONObject data = new JSONObject(new JSONTokener(dots));
			
			Date anchor = new Date(Date.parse(data.getString("anchor")));
			
			JSONArray jRegs = data.getJSONArray("regimens");
			int[] regs =  new int[jRegs.length()];
			for(int i = 0 ; i < regs.length ; ++i) {
				regs[i] = jRegs.getInt(i);
			}
			
			JSONArray jDays = data.getJSONArray("days");
			DotsDay[] days =  new DotsDay[jDays.length()];
			for(int i = 0 ; i < days.length ; ++i) {
				days[i] = DotsDay.deserialize(jDays.getJSONArray(i));
			}
			
			return new DotsData(anchor, regs, days);
		} catch(JSONException e) {
			throw new RuntimeException(e);
		}
		

	}
	
	public static DotsData CreateDotsData(int[] regType, Date anchor) {
		DotsDay[] days = new DotsDay[21];
		for(int j = 0 ; j <  days.length ; ++j ) {
			days[j] = new DotsDay(emptyBoxes(regType));
		}
		
		DotsData data = new DotsData(anchor, regType, days);
		return data;
	}
	
	public static DotsBox[][] emptyBoxes(int[] lengths) {
		DotsBox[][] boxes = new DotsBox[lengths.length][];
		for(int i = 0 ; i < lengths.length; ++i ) {
			boxes[i] = new DotsBox[lengths[i]];
			for(int j = 0 ; j <  boxes[i].length ; ++j ) {
				boxes[i][j] = new DotsBox(MedStatus.unchecked, ReportType.pillbox);
			}
		}
		return boxes;
	}
}
