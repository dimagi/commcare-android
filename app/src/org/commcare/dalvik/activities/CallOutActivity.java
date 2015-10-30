package org.commcare.dalvik.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Toast;

import org.commcare.dalvik.dialogs.PaneledChoiceDialog;
import org.javarosa.core.services.locale.Localization;

import java.util.Date;

/**
 * @author ctsims
 *
 */
public class CallOutActivity extends Activity {

    public static final String PHONE_NUMBER = "cos_pn";
    public static final String CALL_DURATION = "cos_pd";
    public static final String RETURNING = "cos_return";
    public static final String INCOMING_ACTION = "cos_inac";
    
    private static final int DIALOG_NUMBER_ACTION = 0;
    
    private static final int SMS_RESULT = 0;
    private static final int CALL_RESULT = 1;

    private static String number;

    
    TelephonyManager tManager;
    CallListener listener;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
        listener = new CallListener(); 
                
        number = this.getIntent().getStringExtra(PHONE_NUMBER);
        
        if(this.getIntent().hasExtra(INCOMING_ACTION)) {
            dispatchAction(this.getIntent().getStringExtra(INCOMING_ACTION));
        } else {
            showChoiceDialog();
        }
    }
    
    protected void onResume() {
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
    
    
    private void showChoiceDialog() {
        PaneledChoiceDialog dialog = new PaneledChoiceDialog(this,
                PaneledChoiceDialog.ChoiceDialogType.TWO_PANEL, "Select Action");

        View.OnClickListener callListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchAction(Intent.ACTION_CALL);
            }
        };
        dialog.addPanel1("Call", -1, callListener);

        View.OnClickListener smsListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchAction(Intent.ACTION_SENDTO);
            }
        };
        dialog.addPanel2("Send SMS", -1, smsListener);

        dialog.setOnCancelListener(new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                Intent i = new Intent(getIntent());
                setResult(RESULT_CANCELED, i);
                finish();
                return;
            }
        });

        dialog.show();
    }
    
    private void dispatchAction(String action) {
        // using createChooser to handle any errors gracefully
        if(Intent.ACTION_CALL.equals(action) ) {
            tManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
            
            Intent call = new Intent(Intent.ACTION_CALL);
            call.setData(Uri.parse("tel:" + number));
            if(call.resolveActivity(getPackageManager()) != null){
                startActivityForResult(call, CALL_RESULT);
            } else {
                Toast.makeText(this, Localization.get("callout.failure.dialer"), Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {                    
            Intent sms = new Intent(Intent.ACTION_SENDTO);
            sms.setData(Uri.parse("smsto:" + number));
            if(sms.resolveActivity(getPackageManager()) != null){
                startActivityForResult(sms, SMS_RESULT);
            } else {
                Toast.makeText(this, Localization.get("callout.failure.sms"), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if(requestCode == SMS_RESULT || requestCode == CALL_RESULT) {
            //we're done here
            Intent i = new Intent(getIntent());
            
            setResult(RESULT_CANCELED, i);
            finish();
        }
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
