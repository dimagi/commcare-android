/**
 * 
 */
package org.commcare.activities;

import java.util.Date;
import java.util.HashMap;

import org.javarosa.core.model.utils.DateUtils;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Pair;

/**
 * @author ctsims
 *
 */
public class ReminderThread {
    Runnable runnable;
    Thread myThread;
    
    HashMap<String, String> keyToValue;
    HashMap<String, String> valueToKey;
    
    private static final String CASE_TYPE = "patient";
    
    Context mContext;
    
    Cursor c;
    
    boolean mContinuePolling =true;
    
    static final int POLL_PERIOD_SECONDS = 10;
    static final long TIME_PERIOD_SNOOZE_MS = 5 * 60 * 1000;
    
    HashMap<String, Long> recentTriggers = new HashMap<String, Long>();
    
    
    class CommCareLoggedOutException extends Exception {
        
    }
    
    public ReminderThread(Context context) {
        valueToKey = new HashMap<>();
        
        keyToValue = new HashMap<>();
        keyToValue.put("last_exam_time", "minutes_until_next_exam");
        keyToValue.put("last_pv_time", "pv_reminder_minutes"); 
        
        for(String key : keyToValue.keySet()) {
            valueToKey.put(keyToValue.get(key), key);
        }
        
        this.mContext = context;
        
        runnable = new Runnable() {
            @Override
            public void run() {
                while(mContinuePolling) {
                    long current = System.currentTimeMillis();
                    double currentDaysInDouble = DateUtils.fractionalDaysSinceEpoch(new Date());
                    ContentProviderClient cpc = null;
                    ContentProviderClient fieldProvider = null;
                    Cursor caseWalker = null;
                    Cursor fieldWalker = null;
                    String match = null;
                    String snoozeMatch = null;
                    
                    boolean screenOff = !((PowerManager)mContext.getSystemService(Context.POWER_SERVICE)).isScreenOn();
                    
                    try {
                    ContentResolver cr = mContext.getContentResolver();
                    cpc = cr.acquireContentProviderClient(Uri.parse("content://org.commcare.dalvik.case/casedb/case"));

                    //CASE_TYPE: Need to set this dynamically
                    //caseWalker iterates over cases
                    caseWalker = cpc.query(Uri.parse("content://org.commcare.dalvik.case/casedb/case"), 
                    					null, "case_type = ? AND\nstatus=?", new String[] { CASE_TYPE,"open" }, null); 

                    if(caseWalker == null) {
                        throw new CommCareLoggedOutException();
                    }
                    
                    boolean more = caseWalker.moveToFirst();
                    
                    //Iterate over all the cases
                    while(more) {
                        String caseId = caseWalker.getString(caseWalker.getColumnIndex("case_id"));
                        if(fieldProvider == null) {
                            fieldProvider = cr.acquireContentProviderClient(Uri.parse("content://org.commcare.dalvik.case/casedb/data/" + caseId));
                        }
                        fieldWalker = fieldProvider.query(Uri.parse("content://org.commcare.dalvik.case/casedb/data/" + caseId), null, null, null, null);
                        
                        boolean moreFields = fieldWalker.moveToFirst();
                        
                        HashMap<String, Pair<Double, Integer>> dataFields = new HashMap<String, Pair<Double, Integer>>();
                        
                        //Iterate over all fields of the current case
                        while(moreFields) {
                        	//Get the name of the current field
                            String datumName = fieldWalker.getString(fieldWalker.getColumnIndex("datum_id"));
                            
                            //If it's one of the fields that we're interested in, we store its name and value in dataFields
                            if(valueToKey.containsKey(datumName)) {
                                String value = fieldWalker.getString(fieldWalker.getColumnIndex("value"));
                                String keyName = valueToKey.get(datumName);
                                try {
                                    Integer valueFormatted = Integer.parseInt(value);
                                    Pair<Double, Integer> pair = new Pair<Double, Integer>(null, valueFormatted);
                                        
                                    
                                    if(dataFields.containsKey(keyName)) {
                                        pair = dataFields.get(keyName);
                                        pair = Pair.create(pair.first, valueFormatted);
                                    }
                                    dataFields.put(keyName, pair);
                                } catch(NumberFormatException nfe) {
                                    
                                }
                            }

                            //If it's the other field that we're interested in, we store its name and value in dataFields
                            if(keyToValue.containsKey(datumName)) {
                                String timeMeasured = fieldWalker.getString(fieldWalker.getColumnIndex("value"));
                                
                                try {
                                    double days = Double.parseDouble(timeMeasured);
                                    Pair<Double, Integer> pair = new Pair<Double, Integer>(days, null);
                                    if(dataFields.containsKey(datumName)) {
                                        pair = dataFields.get(datumName);
                                        pair = Pair.create(days, pair.second);
                                    }
                                    dataFields.put(datumName, pair);
                                } catch(NumberFormatException nfe) {
                                    
                                }
                            }
                            
                            moreFields = fieldWalker.moveToNext();
                        }
                        
                        //If this case is overdue, put it in recentTriggers and set match to true
                        for(String key : dataFields.keySet()){
                            Pair<Double, Integer> pair = dataFields.get(key);
                            if(pair.first == null || pair.second == null) {
                                continue;
                            }
                            int minutesSince = (int)((currentDaysInDouble - pair.first) * 24 * 60);
                            
                            int timerExpiresOn = (pair.second);
                            if(minutesSince >= timerExpiresOn) {
                                if(!recentTriggers.containsKey(caseId)) { 
                                    match = caseId;
                                    recentTriggers.put(caseId, current);
                                } else if((current - recentTriggers.get(caseId)) > TIME_PERIOD_SNOOZE_MS && screenOff) {
                                    match = caseId;
                                    recentTriggers.put(caseId, current);
                                }
                            }
                            
                        }
                        
                        more = caseWalker.moveToNext();
                    }
                    
                    } catch(RuntimeException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch(CommCareLoggedOutException clo) {
                        
                    }
                    
                    if(cpc != null) {
                        cpc.release();
                    }
                    
                    if(c != null) {
                        c = null;
                    }
                    if(fieldProvider != null) {
                        fieldProvider.release();
                    }
                    
                    if(match != null) {
                        playNoise();
                    } else if(snoozeMatch != null) {
                        
                    }
                    
                    long delay = (System.currentTimeMillis() - current);
                    
                    System.out.println("Polled in: " + delay );
                    
                    try {
                        Thread.sleep(POLL_PERIOD_SECONDS * 1000 - delay);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                        mContinuePolling = false;
                    }
                }
            }
            
            
        };
        
        myThread = new Thread(runnable);
    }
    
    public void playNoise() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(mContext, notification);
            r.play();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    public void startPolling() {
        synchronized(myThread) {
            if(!myThread.isAlive()) {
                myThread.start();
            }
        }
    }
    
    public void stopService() {
        synchronized(myThread) {
            mContinuePolling = false;
        }
    }
}
