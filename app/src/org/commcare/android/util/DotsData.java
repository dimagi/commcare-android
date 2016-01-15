package org.commcare.android.util;

import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Date;

/**
 * @author ctsims
 */
public class DotsData {
    Date anchor;
    
    int[] regimens;
    
    DotsDay[] days;
    
    //Labels within each regimen for what kind of dose new days should be 
    final int[][] regLabels;
    
    private static final int[][] equivM = new int[][] {
        new int[] {0,-1,-1,-1},
        new int[] {0,-1,1,-1},
        new int[] {0,1, 2,-1},
        new int[] {0,1,2, 3}
    };
    
    public enum MedStatus {
        unchecked,
        full,
        empty,
        partial
    }
    
    public enum ReportType {
        direct,
        pillbox,
        self
    }
    
    public static final class DotsBox {
        
        final MedStatus status;
        final String missedMeds;
        final ReportType type;
        final int doseLabel;

        public DotsBox(MedStatus status, ReportType type, String missedMeds, int doselabel) {
            this.status = status;
            this.missedMeds = missedMeds;
            this.type = type;
            this.doseLabel = doselabel;
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
        
        public int getDoseLabel() {
            return doseLabel;
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
            int label = -1;
            if(box.length() > 3) {
                label= box.getInt(3);
            }
            
            String status = box.getString(0);
            String type = box.getString(1);
            return new DotsBox(MedStatus.valueOf(status), ReportType.valueOf(type), missed, label); 
        }
        
        public JSONArray serialize() {
            JSONArray ser = new JSONArray();
            ser.put(status.toString());
            ser.put(type.toString());
            if(missedMeds != null){
                ser.put(missedMeds);
            } else {
                ser.put("");
            }
            ser.put(doseLabel);
            return ser;
        }

        public DotsBox update(DotsBox deserialize) {
            return new DotsBox(deserialize.status,deserialize.type, 
                    deserialize.missedMeds == null? this.missedMeds : deserialize.missedMeds,
                    deserialize.doseLabel == -1 ? this.doseLabel : deserialize.doseLabel);
        }
    }
    
    public static final class DotsDay {
        
        final DotsBox[][] boxes;
        
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
            for (DotsBox[] dotBoxes : boxes) {
                for (int j = 0; j < dotBoxes.length; ++j) {
                    if (dotBoxes[j].status != MedStatus.unchecked) {
                        return false;
                    }
                }
            }
            return true;
        }
        
        public JSONArray serialize() {
            JSONArray day = new JSONArray();
            for (DotsBox[] dotBoxes : boxes) {
                JSONArray regimen = new JSONArray();
                for (int j = 0; j < dotBoxes.length; ++j) {
                    regimen.put(dotBoxes[j].serialize());
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
//            int max = 0;
//            for(DotsBox[] reg : boxes) {
//                if(reg.length > max) {
//                    max = reg.length;
//                }
//            }
//            return max;
        }
        
        /**
         * Takes in a index between 0 and 3 representing a potential dose, and
         * returns either an index between 0 and 3 representing the actual dose
         * window (AM, Noon, etc) represented by the box at that index. 
         * 
         * @param regimenIndex the index of a potential dose (AM,noon,pm, etc)
         * which may be occurring. 
         */
        public int[] getRegIndexes(int regimenIndex) {
            int max = this.getMaxReg();
            int[] retVal = new int[boxes.length];
            //ART v. non-ART
            regimen:
            for(int i = 0 ; i < boxes.length ; ++i) {
                if(boxes[i].length == 0) {
                    retVal[i] = -1;
                } else {
                    //See if there's an explicitly labeled index for
                    //this regimen
                    for(int j = 0 ; j < boxes[i].length; ++j) {
                        if(boxes[i][j].doseLabel == regimenIndex) {
                            retVal[i] = j;
                            continue regimen;
                        }
                    }
                    
                    //otherwise, grab what the default one should be
                    int defaultIndex = equivM[boxes[i].length -1 ][regimenIndex];
                    
                    //Make sure the default index is either unused (-1), or if not, isn't
                    //actually pointing to something already 
                    if(defaultIndex == -1 || boxes[i][defaultIndex].doseLabel == -1) {
                        retVal[i] = defaultIndex;
                    } else {
                        retVal[i] = -1;
                    }
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
            for (DotsBox[] dotBoxes : boxes) {
                for (int j = 0; j < dotBoxes.length; ++j) {
                    DotsBox b = dotBoxes[j];
                    if (ret == null) {
                        if (b.status != MedStatus.unchecked) {
                            ret = MedStatus.empty;
                        } else {
                            ret = MedStatus.unchecked;
                        }
                    } else {
                        if (ret == MedStatus.unchecked) {
                            if (b.status != MedStatus.unchecked) {
                                return MedStatus.partial;
                            }
                        } else if (ret == MedStatus.empty) {
                            if (b.status == MedStatus.unchecked) {
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
    

    private DotsData(Date anchor, int[] regimens, DotsDay[] days, int[][] regLabels) {
        this.anchor = anchor;
        this.regimens = regimens;
        this.days = days;
        this.regLabels = regLabels;
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
                newDays[i] = new DotsDay(emptyBoxes(this.regimens, this.regLabels));
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
            if(this.regLabels != null) {
                JSONArray regLabelJson = new JSONArray();
                for (int[] labels : regLabels) {
                    JSONArray regLabelSub = new JSONArray();
                    for (int j = 0; j < labels.length; ++j) {
                        regLabelSub.put(labels[j]);
                    }
                    regLabelJson.put(regLabelSub);
                }
                object.put("regimen_labels", regLabelJson);
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
            
            int[][] regLabels = new int[regs.length][];
            if(data.has("regimen_labels")) {
                JSONArray jRegLabels = data.getJSONArray("regimen_labels");
                if(jRegLabels.length() != regs.length) {
                    //TODO: specific exception type here
                    throw new RuntimeException("Invalid DOTS model! Regimens and Labels are incompatible lengths");
                }
                for(int i = 0 ; i < jRegLabels.length() ; ++i) {
                    JSONArray jLabels = jRegLabels.getJSONArray(i);
                    regLabels[i] = new int[jLabels.length()];
                    for(int j = 0 ; j < jLabels.length() ; ++j) {
                        regLabels[i][j] = jLabels.getInt(j); 
                    }
                }
            } else {
                //No default regimen labels
                regLabels = null;
            }
            
            JSONArray jDays = data.getJSONArray("days");
            DotsDay[] days =  new DotsDay[jDays.length()];
            for(int i = 0 ; i < days.length ; ++i) {
                days[i] = DotsDay.deserialize(jDays.getJSONArray(i));
            }
            
            return new DotsData(anchor, regs, days, regLabels);
        } catch(JSONException e) {
            throw new RuntimeException(e);
        }
        

    }
    
    public static DotsData CreateDotsData(int[] regType, Date anchor) {
        DotsDay[] days = new DotsDay[21];
        for(int j = 0 ; j <  days.length ; ++j ) {
            days[j] = new DotsDay(emptyBoxes(regType, null));
        }

        return new DotsData(anchor, regType, days, null);
    }
    
    public static DotsBox[][] emptyBoxes(int[] lengths, int[][] regLabels) {
        DotsBox[][] boxes = new DotsBox[lengths.length][];
        for(int i = 0 ; i < lengths.length; ++i ) {
            boxes[i] = new DotsBox[lengths[i]];
            for(int j = 0 ; j <  boxes[i].length ; ++j ) {
                int label = -1;
                if(regLabels != null) {
                    label = regLabels[i][j];
                }
                boxes[i][j] = new DotsBox(MedStatus.unchecked, ReportType.pillbox, null, label);
            }
        }
        return boxes;
    }
}
