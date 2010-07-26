/**
 * 
 */
package org.commcare.android.activities;

import java.util.Date;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

/**
 * @author ctsims
 *
 */
public class CallOutActivity extends Activity {

	public static final String PHONE_NUMBER = "cos_pn";
	public static final String CALL_DURATION = "cos_pd";
	public static final String RETURNING = "cos_return";
	
	TelephonyManager tManager;
	CallListener listener;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
        listener = new CallListener(); 
        
        tManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
        
        Intent call = new Intent(Intent.ACTION_CALL);
        call.setData(Uri.parse("tel:" + this.getIntent().getStringExtra(PHONE_NUMBER)));
        startActivity(call);
    }
    
    public void onResume() {
    	super.onResume();
    	if(listener.isFinished()) {
        	long duration = listener.getCallDuration();
        	if(duration > 0) {
		        Intent i = new Intent(getIntent());
		        i.putExtra(CALL_DURATION, duration);
		        
		        setResult(RESULT_OK, i);
		        finish();
		        return;
        	} else {
        		//TODO: We could also pop up a thing here that said "Phone call in progress"
        		//or something
        		Intent i = new Intent(getIntent());
		        
		        setResult(RESULT_CANCELED, i);
		        finish();
		        return;
        	}
        } 
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
    }

    public class CallListener extends PhoneStateListener {
        boolean called = false;
        long started;
        long duration;
        boolean finished = false;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);

            // Don't fire before the call was made
            if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                called = true;
                started = new Date().getTime();
            }

            // Call has ended -- now bring the activity back to front
            if (called && state == TelephonyManager.CALL_STATE_IDLE) {
                called = false;
                tManager.listen(this, PhoneStateListener.LISTEN_NONE);
                
                duration = new Date().getTime() - started;
                finished = true;
                
                //TODO: Any way to skip the stupid Call Log?
                
            	if(duration > 0) {
    		        Intent i = new Intent(getIntent());
    		        i.putExtra(CALL_DURATION, duration);
    		         
    		        setResult(RESULT_OK, i);
    		        finish();
    		        return;
            	} else {
            		Intent i = new Intent(getIntent());
    		        setResult(RESULT_CANCELED, i);
    		        finish();
    		        return;
            	}
                
            }
        }
        
        public long getCallDuration() {
        	return duration;
        }
        
        public boolean isFinished() {
        	return finished;
        }
    }
}
