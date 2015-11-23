package org.commcare.dalvik.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.android.util.DialogCreationHelpers;
import org.javarosa.core.services.locale.Localization;

import java.util.Date;

/**
 * @author ctsims
 *
 */
public class CallOutActivity extends Activity
        implements RuntimePermissionRequester {

    public static final String PHONE_NUMBER = "cos_pn";
    public static final String CALL_DURATION = "cos_pd";
    public static final String INCOMING_ACTION = "cos_inac";

    private static final String CALLOUT_ACTION_KEY = "callout-action-key";
    
    private static final int DIALOG_NUMBER_ACTION = 0;
    
    private static final int SMS_RESULT = 0;
    private static final int CALL_RESULT = 1;

    private static final int CALL_OR_SMS_PERMISSION_REQUEST = 1;

    private static String number;
    private String calloutAction;

    TelephonyManager tManager;
    CallListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
        listener = new CallListener(); 
                
        number = getIntent().getStringExtra(PHONE_NUMBER);
        loadStateFromInstance(savedInstanceState);

        if (getIntent().hasExtra(INCOMING_ACTION)) {
            calloutAction = getIntent().getStringExtra(INCOMING_ACTION);
            dispatchActionWithPermissions();
        } else {
            this.showDialog(DIALOG_NUMBER_ACTION);
        }
    }

    private void loadStateFromInstance(Bundle savedInstanceState) {
        if (savedInstanceState.containsKey(CALLOUT_ACTION_KEY)) {
            calloutAction = savedInstanceState.getString(CALLOUT_ACTION_KEY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(listener.isFinished()) {
            long duration = listener.getCallDuration();
            if(duration > 0) {
                Intent i = new Intent(getIntent());
                i.putExtra(CALL_DURATION, duration);
                
                setResult(RESULT_OK, i);
                finish();
            } else {
                //TODO: We could also pop up a thing here that said "Phone call in progress"
                //or something
                Intent i = new Intent(getIntent());
                
                setResult(RESULT_CANCELED, i);
                finish();
            }
        } 
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_NUMBER_ACTION:
            final CharSequence[] items = {"Call", "Send SMS"};

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Action");
            builder.setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    if (item == 0) {
                        calloutAction = Intent.ACTION_CALL;
                    } else {
                        calloutAction = Intent.ACTION_SENDTO;
                    }
                    dispatchActionWithPermissions();
                }
            });
            builder.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    Intent i = new Intent(getIntent());
                    
                    setResult(RESULT_CANCELED, i);
                    finish();
                }
            });
            return builder.create();
        }
        return null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(CALLOUT_ACTION_KEY, calloutAction);
    }

    private void dispatchActionWithPermissions() {
        if (checkPhonePermissions()) {
            if (shouldShowPhonePermissionRationale()) {
                AlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                                "Send sms or call phone",
                                "CommCare would like to call a phone or send an sms...");
                dialog.show();
            } else {
                requestNeededPermissions();
            }
        } else {
            dispatchAction();
        }
    }

    private String getPermissionForAction() {
        if (calloutAction.equals(Intent.ACTION_CALL)) {
            return Manifest.permission.CALL_PHONE;
        } else if (calloutAction.equals(Intent.ACTION_SENDTO)) {
            return Manifest.permission.RECEIVE_SMS;
        }
        return "";
    }

    private boolean checkPhonePermissions() {
        return ContextCompat.checkSelfPermission(this, getPermissionForAction()) != PackageManager.PERMISSION_GRANTED;
    }

    private boolean shouldShowPhonePermissionRationale() {
        return ActivityCompat.shouldShowRequestPermissionRationale(this,
                getPermissionForAction());
    }

    @Override
    public void requestNeededPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{getPermissionForAction()},
                CALL_OR_SMS_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CALL_OR_SMS_PERMISSION_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                if (getPermissionForAction().equals(permissions[i]) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    dispatchAction();
                }
            }
        }
    }

    private void dispatchAction() {
        // using createChooser to handle any errors gracefully
        if(Intent.ACTION_CALL.equals(calloutAction) ) {
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

    @Override
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
                } else {
                    Intent i = new Intent(getIntent());
                    setResult(RESULT_CANCELED, i);
                    finish();
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
